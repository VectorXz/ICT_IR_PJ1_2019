
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
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
		long startTime;
		long endTime;
		startTime = System.currentTimeMillis(); 
		for (File block : dirlist) {

			HashSet<Pair<Integer, Integer>> tempTermDoc = new HashSet<Pair<Integer, Integer>>();

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
						Pair<Integer, Integer> t = null;
						int termId;
						if (termDict.containsKey(token)) {
							//System.out.println("Exists!");
							termId = termDict.get(token);
							t = new Pair<Integer, Integer>(termId, docId);
							tempTermDoc.add(t);
						} else {
							termDict.put(token, ++wordIdCounter);
							//System.out.println("Added!");
							termId = termDict.get(token);
							t = new Pair<Integer, Integer>(termId, docId);
							tempTermDoc.add(t);
						}
					}
				}
				
				//System.out.println("ended "+docId);
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
			List<Pair<Integer, Integer>> tempTermDoc2 = new ArrayList<Pair<Integer, Integer>>(tempTermDoc);
			Collections.sort(tempTermDoc2, new ListComparator());
			// System.out.println(bfc);
			// System.out.println(tempTermDoc.size());
			//System.out.println(tempTermDoc2);

			PostingList temp = null;
			TreeSet<Integer> docList = new TreeSet<Integer>();
			for (Pair<Integer, Integer> item : tempTermDoc2) {
				if (temp == null) {
					temp = new PostingList(item.getFirst());
					docList.add(item.getSecond());
				} else if (item.getFirst() == temp.getTermId()) {
					docList.add(item.getSecond());
				} else {
					List<Integer> tList = new ArrayList<Integer>(docList);
					temp = new PostingList(temp.getTermId(), tList);
					writePosting(bfc.getChannel(), temp);
					temp = new PostingList(item.getFirst());
					docList.clear();
					docList.add(item.getSecond());
				}
				Pair<Long, Integer> tempPair = new Pair<Long, Integer>(null, temp.getList().size());
				postingDict.put(item.getFirst(), tempPair);
			}
			List<Integer> tList = new ArrayList<Integer>(docList);
			temp = new PostingList(temp.getTermId(), tList);
			writePosting(bfc.getChannel(), temp);
			
			//System.out.println(postingDict);

			bfc.close();
		}
		endTime = System.currentTimeMillis();
		System.out.println("\t[Build Terms + Write posting] Time Used: "+((endTime - startTime)/1000.0)+" secs\n");
		

		/* Required: output total number of files. */
		// System.out.println("Total Files Indexed: "+totalFileCount);
		
		/* Merge blocks */
		startTime = System.currentTimeMillis();
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
			
			PostingList l1 = index.readPosting(bf1.getChannel());
			PostingList l2 = index.readPosting(bf2.getChannel());
			PostingList merged = null;
			TreeSet<Integer> docList = null;
			List<Integer> converter = null;
			
			while(l1 != null && l2 != null) {
				docList = new TreeSet<Integer>();
				if(l1.getTermId() == l2.getTermId()) {
					for (Integer docId : l1.getList()) {
						docList.add(docId);
					}
					for (Integer docId : l2.getList()) {
						docList.add(docId);
					}
					converter = new ArrayList<Integer>(docList);
					merged = new PostingList(l1.getTermId(), converter);
					writePosting(mf.getChannel(), merged);
					postingDict.get(l1.getTermId()).setSecond(merged.getList().size());
					
					l1 = index.readPosting(bf1.getChannel());
					l2 = index.readPosting(bf2.getChannel());
				} else if (l1.getTermId() < l2.getTermId()) {
					//save l1
					for (Integer docId : l1.getList()) {
						docList.add(docId);
					}
					converter = new ArrayList<Integer>(docList);
					merged = new PostingList(l1.getTermId(), converter);
					writePosting(mf.getChannel(), merged);
					postingDict.get(l1.getTermId()).setSecond(merged.getList().size());
					
					l1 = index.readPosting(bf1.getChannel());
				} else if (l2.getTermId() < l1.getTermId()) {
					//save l2
					for (Integer docId : l2.getList()) {
						docList.add(docId);
					}
					converter = new ArrayList<Integer>(docList);
					merged = new PostingList(l2.getTermId(), converter);
					writePosting(mf.getChannel(), merged);
					postingDict.get(l2.getTermId()).setSecond(merged.getList().size());
					
					l2 = index.readPosting(bf2.getChannel());
				}
			}
			
			if(l1 == null) {
				//keep L2
				while(l2 != null) {
					docList = new TreeSet<Integer>();
					for (Integer docId : l2.getList()) {
						docList.add(docId);
					}
					converter = new ArrayList<Integer>(docList);
					merged = new PostingList(l2.getTermId(), converter);
					writePosting(mf.getChannel(), merged);
					postingDict.get(l2.getTermId()).setSecond(merged.getList().size());
					
					l2 = index.readPosting(bf2.getChannel());
				}
			} else if(l2 == null) {
				//keep L1
				while(l1 != null) {
					docList = new TreeSet<Integer>();
					for (Integer docId : l1.getList()) {
						docList.add(docId);
					}
					converter = new ArrayList<Integer>(docList);
					merged = new PostingList(l1.getTermId(), converter);
					writePosting(mf.getChannel(), merged);
					postingDict.get(l1.getTermId()).setSecond(merged.getList().size());
					
					l1 = index.readPosting(bf1.getChannel());
				}
			}
			
			bf1.close();
			bf2.close();
			mf.close();
			b1.delete(); // comment to keep tempIndex file
			b2.delete(); // comment to keep tempIndex file
			blockQueue.add(combfile);
		}
		endTime = System.currentTimeMillis();
		System.out.println("\t[Merge Time] Time Used: "+((endTime - startTime)/1000.0)+" secs\n");

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