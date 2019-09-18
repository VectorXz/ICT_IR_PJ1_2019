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
		
		return null;
	}

	@Override
	public void writePosting(FileChannel fc, PostingList p) {
		/*
		 * TODO: Your code here
		 *       Write the given postings list to the given file.
		 */
		//System.out.println("Hello World!");
		
		int totalBytes = (2+p.getList().size())*4;
		ByteBuffer bb = ByteBuffer.allocate(totalBytes);
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

