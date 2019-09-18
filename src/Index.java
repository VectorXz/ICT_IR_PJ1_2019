
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Index {

	// Term id -> (position in index file, doc frequency) dictionary
	private static Map<Integer, Pair<Long, Integer>> postingDict = new TreeMap<Integer, Pair<Long, Integer>>();
	// Doc name -> doc id dictionary
	private static Map<String, Integer> docDict = new TreeMap<String, Integer>();
	// Term -> term id dictionary
	private static Map<String, Integer> termDict = new TreeMap<String, Integer>();
	// Block queue
	private static LinkedList<File> blockQueue = new LinkedList<File>();

	// Total file counter
	private static int totalFileCount = 0;
	// Document counter
	private static int docIdCounter = 0;
	// Term counter
	private static int wordIdCounter = 0;
	// Index
	private static BaseIndex index = null;

	/*
	 * Write a posting list to the given file You should record the file position of
	 * this posting list so that you can read it back during retrieval
	 * 
	 */
	private static void writePosting(FileChannel fc, PostingList posting) throws IOException {
		/*
		 * TODO: Your code here
		 * 
		 */
		//System.out.println("I'm going to write " + posting.getTermId());
		postingDict.get(posting.getTermId()).setFirst(fc.position());
		index.writePosting(fc, posting);
	}

	/**
	 * Pop next element if there is one, otherwise return null
	 * 
	 * @param iter an iterator that contains integers
	 * @return next element or null
	 */
	private static Integer popNextOrNull(Iterator<Integer> iter) {
		if (iter.hasNext()) {
			return iter.next();
		} else {
			return null;
		}
	}

	/*
	 * Delete file and directory recursively
	 */
	public static void delete(File f) {
		// check if it's directory or not? if yes we need to delete all things in it
		// recursively
		if (f.isDirectory()) {
			// check if directory is empty so delete it now
			if (f.list().length == 0) {
				if (f.delete()) {
					// System.out.println("Deleted directory : "+f.getName());
				} else {
					// System.out.println("Cannot Delete directory : "+f.getName());
				}
			} else {
				// if it's not empty so travel through it and delete all things
				// delete all sub files/folders in that directory recursively
				for (String subf : f.list()) {
					File delf = new File(f, subf);
					// recursive part
					delete(delf);
				}

				// check parent folder is empty yet? if yes delete parent folder too.
				if (f.list().length == 0) {
					if (f.delete()) {
						// System.out.println("Deleted directory : "+f.getName());
					} else {
						// System.out.println("Cannot Delete directory : "+f.getName());
					}
				}
			}
		} else {
			// if it's not directory so we can delete it now
			if (f.delete()) {
				// System.out.println("Deleted File : "+f.getName());
			} else {
				// System.out.println("Cannot Delete File : "+f.getName());
			}
		}
	}

	/*
	 * Comparator For Comparing Pair of Integer
	 */

	public static class ListComparator implements Comparator<Pair<Integer, Integer>> {

		@Override
		public int compare(Pair<Integer, Integer> arg0, Pair<Integer, Integer> arg1) {
			// TODO Auto-generated method stub
			return arg0.getFirst()-arg1.getFirst();
		}
		// Compare Pair<Integer, Integer> elements ...
	}

	/**
	 * Main method to start the indexing process.
	 * 
	 * @param method        :Indexing method. "Basic" by default, but extra credit
	 *                      will be given for those who can implement variable byte
	 *                      (VB) or Gamma index compression algorithm
	 * @param dataDirname   :relative path to the dataset root directory. E.g.
	 *                      "./datasets/small"
	 * @param outputDirname :relative path to the output directory to store index.
	 *                      You must not assume that this directory exist. If it
	 *                      does, you must clear out the content before indexing.
	 */
	public static int runIndexer(String method, String dataDirname, String outputDirname) throws IOException {
		/* Get index */
		String className = method + "Index";
		try {
			Class<?> indexClass = Class.forName(className);
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get root directory */
		File rootdir = new File(dataDirname);
		if (!rootdir.exists() || !rootdir.isDirectory()) {
			System.err.println("Invalid data directory: " + dataDirname);
			return -1;
		}

		/* Get output directory */
		File outdir = new File(outputDirname);
		if (outdir.exists() && !outdir.isDirectory()) {
			System.err.println("Invalid output directory: " + outputDirname);
			return -1;
		}

		/*
		 * TODO: delete all the files/sub folder under outdir
		 * 
		 */

		for (File f : outdir.listFiles()) {
			delete(f);
		}

		if (!outdir.exists()) {
			if (!outdir.mkdirs()) {
				System.err.println("Create output directory failure");
				return -1;
			}
		}

		/* BSBI indexing algorithm */
		File[] dirlist = rootdir.listFiles();

		/* For each block */
		for (File block : dirlist) {

			List<Pair<Integer, Integer>> tempTermDoc = new ArrayList<Pair<Integer, Integer>>();

			File blockFile = new File(outputDirname, block.getName());
			// System.out.println("Processing block "+block.getName());
			blockQueue.add(blockFile);

			File blockDir = new File(dataDirname, block.getName());
			File[] filelist = blockDir.listFiles();

			/* For each file */
			for (File file : filelist) {
				++totalFileCount;
				String fileName = block.getName() + "/" + file.getName();

				// use pre-increment to ensure docID > 0
				int docId = ++docIdCounter;
				docDict.put(fileName, docId);

				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine()) != null) {
					String[] tokens = line.trim().split("\\s+");
					for (String token : tokens) {
						/*
						 * TODO: Your code here For each term, build up a list of documents in which the
						 * term occurs
						 */

						// System.out.println(token);
						if (termDict.containsKey(token)) {
							// System.out.println("Exists!");
							int termId = termDict.get(token);
							Pair<Integer, Integer> tempPair = new Pair<Integer, Integer>(termId, docId);
							tempTermDoc.add(tempPair);
						} else {
							termDict.put(token, ++wordIdCounter);
							// System.out.println("Added!");
							int termId = termDict.get(token);
							Pair<Integer, Integer> tempPair = new Pair<Integer, Integer>(termId, docId);
							tempTermDoc.add(tempPair);
						}
					}
				}
				reader.close();
			}

			/* Sort and output */
			if (!blockFile.createNewFile()) {
				System.err.println("Create new block failure.");
				return -1;
			}

			RandomAccessFile bfc = new RandomAccessFile(blockFile, "rw");

			/*
			 * TODO: Your code here Write all posting lists for all terms to file (bfc)
			 */

			// PostingList test = new PostingList(5);
			// writePosting(bfc.getChannel(), test);

			// System.out.println(tempTermDoc.size());
			// System.out.println(tempTermDoc);
			Collections.sort(tempTermDoc, new ListComparator());
			// System.out.println(bfc);
			// System.out.println(tempTermDoc.size());
			//System.out.println(tempTermDoc);

			PostingList temp = null;
			for (Pair<Integer, Integer> item : tempTermDoc) {
				if (temp == null) {
					temp = new PostingList(item.getFirst());
					temp.getList().add(item.getSecond());
				} else if (item.getFirst() == temp.getTermId()) {
					if(!temp.getList().contains(item.getSecond())) {
						temp.getList().add(item.getSecond());
					}
				} else {
					writePosting(bfc.getChannel(), temp);
					temp = new PostingList(item.getFirst());
					temp.getList().add(item.getSecond());
				}
				Pair<Long, Integer> tempPair = new Pair<Long, Integer>(null, temp.getList().size());
				postingDict.put(item.getFirst(), tempPair);
			}
			writePosting(bfc.getChannel(), temp);
			
			//System.out.println(postingDict);

			/*
			 * Map<Integer, List<Integer>> postingLists = new TreeMap<Integer,
			 * List<Integer>>();
			 * 
			 * build up final posting lists for (Pair<Integer, Integer> item : tempTermDoc)
			 * { if(!postingLists.containsKey(item.getFirst())) { List<Integer> tempList =
			 * new ArrayList<Integer>(); tempList.add(item.getSecond());
			 * postingLists.put(item.getFirst(), tempList); } else {
			 * if(!postingLists.get(item.getFirst()).contains(item.getSecond())) {
			 * postingLists.get(item.getFirst()).add(item.getSecond()); } } }
			 * System.out.println(postingLists); for (Map.Entry<Integer,List<Integer>>
			 * postingList : postingLists.entrySet()) { PostingList tempPL = new
			 * PostingList(postingList.getKey(),postingList.getValue()); Pair<Long, Integer>
			 * tempPair = new Pair<Long, Integer>(null, tempPL.getList().size());
			 * postingDict.put(postingList.getKey(), tempPair);
			 * writePosting(bfc.getChannel(), tempPL); }
			 */

			/*
			 * WRONG WRITING!
			 */

			/*
			 * for (Pair<Integer, Integer> item : tempTermDoc) {
			 * if(!postingLists.containsKey(item.getFirst())) { List<Integer> tempList = new
			 * ArrayList<Integer>(); tempList.add(item.getSecond());
			 * postingLists.put(item.getFirst(), tempList); } else {
			 * if(!postingLists.get(item.getFirst()).contains(item.getSecond())) {
			 * postingLists.get(item.getFirst()).add(item.getSecond()); } } }
			 * 
			 * System.out.println(postingLists);
			 * 
			 * bfc.seek(0);
			 * 
			 * for (Map.Entry<Integer,List<Integer>> item : postingLists.entrySet()) { int
			 * termID = item.getKey(); int noDoc = item.getValue().size();
			 * bfc.writeInt(termID); bfc.writeInt(noDoc); for (Integer i : item.getValue())
			 * { bfc.writeInt(i); } }
			 */

			bfc.close();
		}

		/* Required: output total number of files. */
		// System.out.println("Total Files Indexed: "+totalFileCount);

		/* Merge blocks */
		while (true) {
			if (blockQueue.size() <= 1)
				break;

			File b1 = blockQueue.removeFirst();
			File b2 = blockQueue.removeFirst();

			File combfile = new File(outputDirname, b1.getName() + "+" + b2.getName());
			if (!combfile.createNewFile()) {
				System.err.println("Create new block failure.");
				return -1;
			}

			RandomAccessFile bf1 = new RandomAccessFile(b1, "r");
			RandomAccessFile bf2 = new RandomAccessFile(b2, "r");
			RandomAccessFile mf = new RandomAccessFile(combfile, "rw");

			/*
			 * TODO: Your code here Combine blocks bf1 and bf2 into our combined file, mf
			 * You will want to consider in what order to merge the two blocks (based on
			 * term ID, perhaps?).
			 * 
			 */
			
			bf1.seek(0);
			bf2.seek(0);
			int first = bf1.readInt();
			int second = bf2.readInt();
			
			int finish = -1;
			//System.out.println("=======");
			//System.out.println(postingDict);
			
			while(true) {
				if(first < second) {
					PostingList pl = new PostingList(first);
					int noDoc = bf1.readInt();
					for(int i=0;i<noDoc;i++) {
						int docId = bf1.readInt();
						pl.getList().add(docId);
					}
					writePosting(mf.getChannel(), pl);
					postingDict.get(first).setSecond(pl.getList().size());
					
					try {
						first = bf1.readInt();
					} catch (IOException ex) {
						// System.out.println("First file is ended!");
						finish = 1;
					}
				} else if(first == second) {
					//System.out.println("Merging"+first+" "+second);
					PostingList tempPL = new PostingList(first);
					int noDoc1 = bf1.readInt();
					int noDoc2 = bf2.readInt();
					for (int i = 0; i < noDoc1; i++) {
						int docId = bf1.readInt();
						if (!tempPL.getList().contains(docId)) {
							tempPL.getList().add(docId);
						}
					}
					for (int i = 0; i < noDoc2; i++) {
						int docId = bf2.readInt();
						// System.out.println("DocID = "+second);
						if (!tempPL.getList().contains(docId)) {
							tempPL.getList().add(docId);
						}
					}
					Collections.sort(tempPL.getList());
					writePosting(mf.getChannel(), tempPL);
					postingDict.get(first).setSecond(tempPL.getList().size());
					//System.out.println(postingDict);
					
					try {
						first = bf1.readInt();
					} catch (IOException ex) {
						// System.out.println("First file is ended!");
						finish = 1;
					}
					try {
						second = bf2.readInt();
					} catch (IOException ex) {
						// System.out.println("Second file is ended!");
						finish = 2;
					}
				} else if (first>second) {
					PostingList pl = new PostingList(second);
					int noDoc = bf2.readInt();
					for(int i=0;i<noDoc;i++) {
						int docId = bf2.readInt();
						pl.getList().add(docId);
					}
					writePosting(mf.getChannel(), pl);
					postingDict.get(first).setSecond(pl.getList().size());
					
					try {
						second = bf2.readInt();
					} catch (IOException ex) {
						// System.out.println("Second file is ended!");
						finish = 2;
					}
				}
				
				if(finish == 1) {
					// System.out.println("Keeping leftovers in file 2");
					while (true) {
						PostingList pl = new PostingList(second);
						int noDoc = bf2.readInt();
						for(int i=0;i<noDoc;i++) {
							int docId = bf2.readInt();
							pl.getList().add(docId);
						}
						writePosting(mf.getChannel(), pl);
						postingDict.get(second).setSecond(pl.getList().size());
						
						try {
							second = bf2.readInt();
						} catch (IOException ex) {
							// System.out.println("Second file is ended!");
							break;
						}
					}
					break;
				} else if(finish == 2) {
					// System.out.println("Keeping leftovers in file 1");
					while (true) {
						PostingList pl = new PostingList(first);
						int noDoc = bf1.readInt();
						for(int i=0;i<noDoc;i++) {
							int docId = bf1.readInt();
							pl.getList().add(docId);
						}
						writePosting(mf.getChannel(), pl);
						postingDict.get(first).setSecond(pl.getList().size());
						
						try {
							first = bf1.readInt();
						} catch (IOException ex) {
							// System.out.println("First file is ended!");
							break;
						}
					}
					break;
				}
			}
			
			//System.out.println("=======");
			//System.out.println(postingDict);
			
			bf1.close();
			bf2.close();
			mf.close();
			b1.delete(); // comment to keep tempIndex file
			b2.delete(); // comment to keep tempIndex file
			blockQueue.add(combfile);
		}

		/* Dump constructed index back into file system */
		File indexFile = blockQueue.removeFirst();
		indexFile.renameTo(new File(outputDirname, "corpus.index"));

		BufferedWriter termWriter = new BufferedWriter(new FileWriter(new File(outputDirname, "term.dict")));
		for (String term : termDict.keySet()) {
			termWriter.write(term + "\t" + termDict.get(term) + "\n");
		}
		termWriter.close();

		BufferedWriter docWriter = new BufferedWriter(new FileWriter(new File(outputDirname, "doc.dict")));
		for (String doc : docDict.keySet()) {
			docWriter.write(doc + "\t" + docDict.get(doc) + "\n");
		}
		docWriter.close();

		BufferedWriter postWriter = new BufferedWriter(new FileWriter(new File(outputDirname, "posting.dict")));
		for (Integer termId : postingDict.keySet()) {
			postWriter.write(termId + "\t" + postingDict.get(termId).getFirst() + "\t"
					+ postingDict.get(termId).getSecond() + "\n");
		}
		postWriter.close();

		return totalFileCount;
	}

	public static void main(String[] args) throws IOException {
		/* Parse command line */
		if (args.length != 3) {
			System.err.println("Usage: java Index [Basic|VB|Gamma] data_dir output_dir");
			return;
		}

		/* Get index */
		String className = "";
		try {
			className = args[0];
		} catch (Exception e) {
			System.err.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get root directory */
		String root = args[1];

		/* Get output directory */
		String output = args[2];
		runIndexer(className, root, output);
	}

}