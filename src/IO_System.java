import java.util.Arrays;

public class IO_System {
//	public byte [] ldisk; //64 blocks of 64 bytes
	public PackableMemory ldisk = null;
	public final int 	blockSize = 64,
						blockAmount = 64,
						fileDescriptorSize = 16;
	private final int k = 6; //index after bitmap, dir, and file descriptors 
	
	public IO_System(){
		ldisk = new PackableMemory(blockAmount * blockSize);
//		
//		//set bit map to reserve first 7 blocks (1 for bitmap, 6 for file descriptors)
//		ldisk.mem[0] |= 0b11111110;  
//		
//		//set everything after bitmap to be -1
//		for (int i = 1; i < blockAmount; ++i){ 		//go through blocks
//			for (int j = 0; j < blockSize / fileDescriptorSize; ++j){ //go through FileDescriptor size chunks 
//				if (i == 1 && j == 0) //exclude writing to Dir
//					continue;
//				ldisk.pack(-1, i*blockAmount + j); 
//			}
//		}
	}
	
	public void read_block(int blockIndex, byte[] buffer){
		//read contents of input block into buffer
		for (int i = 0; i < blockSize; ++i){
			buffer[i] = ldisk.mem[blockSize * blockIndex  + i];
		}
//		buffer = Arrays.copyOfRange(ldisk.mem, blockSize * blockIndex, blockSize * (blockIndex + 1));
	}
	
	public void write_block(int blockIndex, byte[] buffer){
		//Write contents from input buffer to disk
		for (int i = 0; i < blockSize; ++i){
			 ldisk.mem[blockAmount * blockIndex + i] = buffer[i]; 
		}	
	}
}
