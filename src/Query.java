

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class ListComparator<T extends Comparable<T>> implements Comparator<List<T>> {

	  @Override
	  public int compare(List<T> o1, List<T> o2) {
	    return o1.size()-o2.size();
	  }

	}

public class Query {

	// Term id -> position in index file
	private  Map<Integer, Long> posDict = new TreeMap<Integer, Long>();
	// Term id -> document frequency
	private  Map<Integer, Integer> freqDict = new TreeMap<Integer, Integer>();
	// Doc id -> doc name dictionary
	private  Map<Integer, String> docDict = new TreeMap<Integer, String>();
	// Term -> term id dictionary
	private  Map<String, Integer> termDict = new TreeMap<String, Integer>();
	// Index
	private  BaseIndex index = null;
	

	//indicate whether the query service is running or not
	private boolean running = false;
	private RandomAccessFile indexFile = null;
	
	/* 
	 * Read a posting list with a given termID from the file 
	 * You should seek to the file position of this specific
	 * posting list and read it back.
	 * */
	private  PostingList readPosting(FileChannel fc, int termId)
			throws IOException {
		/*
		 * TODO: Your code here
		 */
		fc.position(posDict.get(termId));
		return index.readPosting(fc);
	}
	
	public List<Integer> listIntersec(List<Integer> l1, List<Integer> l2) {
		List<Integer> result = new ArrayList<Integer>();
		
		if(l1.size() == 0) {
			return result;
		} else if(l2.size() == 0) {
			return result;
		}
		int ptr1 = l1.get(0);
		int ptr2 = l2.get(0);
		int i1 = 0, i2 = 0;
		//System.out.println("Initialize : "+ptr1+" "+ptr2);
		int finish = 0;
		while (true) {
			if(ptr1 == ptr2) {
				//System.out.println(ptr1+"="+ptr2);
				result.add(ptr1);
				//System.out.println("Added : "+result);
				//move ptr
				if(i1<l1.size()-1) {
					ptr1 = l1.get(++i1);
				} else { finish++; }
				if(i2<l2.size()-1) {
					ptr2 = l2.get(++i2);
				} else { finish++; }
			} else if (ptr1 < ptr2) {
				//System.out.println(ptr1+"<"+ptr2);
				//move ptr1
				if(i1<l1.size()-1) {
					ptr1 = l1.get(++i1);
				} else { finish++; }
			} else if (ptr2 < ptr1) {
				//System.out.println(ptr1+">"+ptr2);
				//move ptr2
				if(i2<l2.size()-1) {
					ptr2 = l2.get(++i2);
				} else { finish++; }
			}
			
			if(finish == 2) {
				//System.out.println("All end > break!");
				break;
			}
		}
		//System.out.println(result);
		return result;
	}
	
	
	public void runQueryService(String indexMode, String indexDirname) throws IOException
	{
		//Get the index reader
		try {
			Class<?> indexClass = Class.forName(indexMode+"Index");
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}
		
		//Get Index file
		File inputdir = new File(indexDirname);
		if (!inputdir.exists() || !inputdir.isDirectory()) {
			System.err.println("Invalid index directory: " + indexDirname);
			return;
		}
		
		/* Index file */
		indexFile = new RandomAccessFile(new File(indexDirname,
				"corpus.index"), "r");

		String line = null;
		/* Term dictionary */
		BufferedReader termReader = new BufferedReader(new FileReader(new File(
				indexDirname, "term.dict")));
		while ((line = termReader.readLine()) != null) {
			String[] tokens = line.split("\t");
			termDict.put(tokens[0], Integer.parseInt(tokens[1]));
		}
		termReader.close();

		/* Doc dictionary */
		BufferedReader docReader = new BufferedReader(new FileReader(new File(
				indexDirname, "doc.dict")));
		while ((line = docReader.readLine()) != null) {
			String[] tokens = line.split("\t");
			docDict.put(Integer.parseInt(tokens[1]), tokens[0]);
		}
		docReader.close();

		/* Posting dictionary */
		BufferedReader postReader = new BufferedReader(new FileReader(new File(
				indexDirname, "posting.dict")));
		while ((line = postReader.readLine()) != null) {
			String[] tokens = line.split("\t");
			posDict.put(Integer.parseInt(tokens[0]), Long.parseLong(tokens[1]));
			freqDict.put(Integer.parseInt(tokens[0]),
					Integer.parseInt(tokens[2]));
		}
		postReader.close();
		
		this.running = true;
	}
    
	public List<Integer> retrieve(String query) throws IOException
	{	if(!running) 
		{
			System.err.println("Error: Query service must be initiated");
		}
		
		/*
		 * TODO: Your code here
		 *       Perform query processing with the inverted index.
		 *       return the list of IDs of the documents that match the query
		 *      
		 */
		
		List<Integer> result = new ArrayList<Integer>();
		List<String> words = Arrays.asList(query.split(" "));
		List<List<Integer>> allpl = new ArrayList<List<Integer>>();
		PostingList temp = null;
		int termID;
		for (String word : words) {
			if(termDict.containsKey(word)) {
				termID = termDict.get(word);
				temp = readPosting(indexFile.getChannel(), termID);
				allpl.add(temp.getList());
				//System.out.println(temp.getTermId()+" "+temp.getList());
			} else {
				return result;
			}
		}
		
		//System.out.println(allpl);
		Collections.sort(allpl, new ListComparator<>());
		//System.out.println(allpl);
		
		if(allpl.size() == 1) {
			return allpl.get(0);
		} else {
			int i = 0;
			List<Integer> fin = allpl.get(0);
			while(true) {
				List<Integer> l2 = allpl.get(i+1);
				fin = listIntersec(fin, l2);
				i++;
				if(i==(allpl.size()-1)) {
					break;
				}
			}
			return fin;
		}
	}
	
    String outputQueryResult(List<Integer> res) {
        /*
         * TODO: 
         * 
         * Take the list of documents ID and prepare the search results, sorted by lexicon order. 
         * 
         * E.g.
         * 	0/fine.txt
		 *	0/hello.txt
		 *	1/bye.txt
		 *	2/fine.txt
		 *	2/hello.txt
		 *
		 * If there no matched document, output:
		 * 
		 * no results found
		 * 
         * */
    	StringBuilder str = new StringBuilder();
    	if(res.size()==0) {
    		//System.out.println("no results found");
    		str.append("no results found");
    		return str.toString();
    	} else {
    		for (Integer i : res) {
    			//System.out.println(docDict.get(i));
    			str.append(docDict.get(i)+"\n");
			}
    		return str.toString();
    	}
    }
	
	public static void main(String[] args) throws IOException {
		/* Parse command line */
		if (args.length != 2) {
			System.err.println("Usage: java Query [Basic|VB|Gamma] index_dir");
			return;
		}

		/* Get index */
		String className = null;
		try {
			className = args[0];
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get index directory */
		String input = args[1];
		
		Query queryService = new Query();
		queryService.runQueryService(className, input);
		
		/* Processing queries */
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		/* For each query */
		String line = null;
		while ((line = br.readLine()) != null) {
			List<Integer> hitDocs = queryService.retrieve(line);
			queryService.outputQueryResult(hitDocs);
		}
		
		br.close();
	}
	
	protected void finalize()
	{
		try {
			if(indexFile != null)indexFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

