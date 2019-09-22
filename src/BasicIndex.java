import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


public class BasicIndex implements BaseIndex {

	@Override
	public PostingList readPosting(FileChannel fc) {
		/*
		 * TODO: Your code here
		 *       Read and return the postings list from the given file.
		 */
		ByteBuffer buf = ByteBuffer.allocate(4);
		
		PostingList postl = null;
		int termID, noDoc;
		try {
			fc.read(buf);
			buf.flip();
			termID = buf.getInt();
			buf.clear();
			
			fc.read(buf);
			buf.flip();
			noDoc = buf.getInt();
			buf.clear();
			
			postl = new PostingList(termID);
			for(int i = 0;i<noDoc;i++) {
				fc.read(buf);
				buf.flip();
				postl.getList().add(buf.getInt());
				buf.clear();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			return null;
		}
		if (postl != null) {
			return postl;
		}
		return null;
	}

	@Override
	public void writePosting(FileChannel fc, PostingList p) {
		/*
		 * TODO: Your code here
		 *       Write the given postings list to the given file.
		 */
		//System.out.println("Hello World!");
		
		ByteBuffer bb = ByteBuffer.allocate((2+p.getList().size())*4);
		bb.putInt(p.getTermId());
		bb.putInt(p.getList().size());
		for(int i=0;i<p.getList().size();i++) {
			bb.putInt(p.getList().get(i));
		}
		bb.rewind();
		try {
			fc.write(bb);
			bb.clear();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

