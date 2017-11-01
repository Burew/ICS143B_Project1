import java.util.Arrays;

public class OFT_Entry {
	private PackableMemory pm;
	private int index,
				position,
				length;
	public OFT_Entry(int newIndex, int newPosition){
		pm = new PackableMemory(64);
		Arrays.fill(pm.mem, (byte)-1);
		index = newIndex;
		position = newPosition;
		length = 0;
	}
	
	public byte[] getBuffer(){
		return pm.mem;
	}
	
	public void setBuffer(byte[] blockBuffer){
		//pm.mem = blockBuffer;
		for (int i = 0; i < pm.mem.length; ++i)
			pm.mem[i] = blockBuffer[i];
	}

	public int getIndex() {
		return index;
	}
	
	public int getLength(){
		return length;
	}
	
	public void setLength(int newLength){
		this.length = newLength;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public void free(){
		index = -1;
	}
	
	public boolean isFree(){
		return getIndex() < 0;
	}
	
	public boolean isEmpty(){
		return getLength() < 0;
	}
	
	public void reset(){
		index = position = length = -1;
		Arrays.fill(pm.mem, (byte)-1);
	}
}