import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

public class FileSystem {
	private final int 	blockSize = 64,
			blockAmount = blockSize,
			numCacheBlocks = 7,
			fileDescriptorSize = 16,
			directoryEntrySize = 8;
	IO_System io = null;
	OFT_Entry [] OFT = null;
	PackableMemory cache = null; //cache that represents first 7 bytes of ldisk

	public FileSystem(IO_System newIO){
		this.io = newIO;
		OFT = new OFT_Entry[]{
				new OFT_Entry(7,0), //The Dir, keep it open and at position 0 throughout
				new OFT_Entry(-1,-1),
				new OFT_Entry(-1,-1),
				new OFT_Entry(-1,-1)
		};
	}
	public int create(String symFileName){ //create a new file
		if (getFDIndexByName(symFileName)  != -1 ){ //if it already exists
			return -1;
		}
		//search through file descriptors and fill in a free FD entry
		int fileDescriptorIndex = findFreeDescriptor();
		if (fileDescriptorIndex < 0){
			return -1;
		}
		//set file descriptor length
		cache.pack(0, fileDescriptorIndex);
		
		//search through Directory and fill in a free DIR entry 
		int directoryEntryIndex = getFreeDirEntry();
		
		if (directoryEntryIndex < 0){
			return -1;
		}
		
		//fill in directory entry w/ name and fileIndex -> <name, fileIndex>
		byte[] fileNameBytes;
		try {
			fileNameBytes = symFileName.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			fileNameBytes = symFileName.getBytes();
		}
		for (int i = 0; i < 4; ++i)
			if (i < fileNameBytes.length)
				OFT[0].getBuffer()[directoryEntryIndex + i] = fileNameBytes[i];
			else
				OFT[0].getBuffer()[directoryEntryIndex + i] = (byte) 32; //else add a space
		PackableMemory.pack(OFT[0].getBuffer(), getFDRelativeIndex(fileDescriptorIndex), directoryEntryIndex + 4);
		io.write_block( 7 + OFT[0].getPosition() / 64 , OFT[0].getBuffer());
		return 7 + OFT[0].getPosition() / 64;
	}
	public int destroy(String symFileName){ //destroy file SymFileName
		int FDnumber = getFDIndexByName(symFileName);
		int dirNumber = getDirIndexByName(symFileName);
		
		if (FDnumber== -1)
			return FDnumber;
		
		//save old file descriptor number
		int oldFDindex = getFDAbsoluteIndex(FDnumber);
		int oldFDNumber = getField(oldFDindex, 1);
		
		//free file dir entry 
		int blockNum = dirNumber / 8;
		byte [] tempBlock = new byte[blockSize];
		io.read_block(7 + blockNum, tempBlock);
		PackableMemory.pack( tempBlock, -1, (blockNum % 8) * directoryEntrySize);
		PackableMemory.pack( tempBlock, -1, (blockNum % 8) * directoryEntrySize + 4);
		io.write_block(7 + blockNum, tempBlock);
		
		//go through old file Descriptor entries
		setField(oldFDindex, 0, -1);
		for (int i = 1; i < 4; ++i){
			int testBlockNumber = getField(oldFDindex, i);
			if (testBlockNumber < 0)
				break;
			
			//remove from bitmap
			setBM(testBlockNumber, 0);
			
			//remove from descriptor
			setField(oldFDindex, i, -1);
		}
		
		return 1;
	}
	public int open(String symFileName){
		//get file descriptor
		int FDRelativeindex = getFDIndexByName(symFileName);
		if (FDRelativeindex == -1) //if it doesnt exist, dont open
			return -1;
		int OFTindex = -1;
		
		//get free OFT entry
		for (int i = 1; i < OFT.length; ++i){
			if(OFT[i].isFree()){
				OFTindex = i;
				break;
			}
		}
		
		if (OFTindex == -1)
			return -1;
		
		//read block 0, if it exists
		byte[] tempBuffer = new byte[blockSize];
		int firstBlock = getField(getFDAbsoluteIndex(FDRelativeindex), 1);
		if (firstBlock > 0)
			io.read_block(firstBlock, tempBuffer);
		
		//set all fields for OFT
		OFT[OFTindex].setPosition(0);
		OFT[OFTindex].setIndex(FDRelativeindex);
		OFT[OFTindex].setLength(getField(getFDAbsoluteIndex(FDRelativeindex), 0)); //maybe have a func to count
		OFT[OFTindex].setBuffer(tempBuffer);
		return OFTindex;
	}
	public int close(int OFTindex){
		if (OFTindex < 0 || OFTindex > 3 || OFT[OFTindex].isFree())
			return -1;
		OFT[OFTindex].free();
		return 1;
	}
	public int read(int index, byte[] memArea, int count){ //read from OFT to memArea to display to user
		if (index < 0 || index > 3) //check bounds
			return -1;
		if ( OFT[index].isFree() || OFT[index].isEmpty()) //check file contents
			return -1;
		
		int relativePosition = OFT[index].getPosition() % 64;
		int bytesRead = 0;
		while (count != 0 && OFT[index].getPosition() < OFT[index].getLength()){
			while (count != 0  &&
					!positionAtEndofBlock(relativePosition) && 
					OFT[index].getPosition() < OFT[index].getLength()){
				memArea[bytesRead] = OFT[index].getBuffer()[relativePosition];
				OFT[index].setPosition(OFT[index].getPosition() + 1);
				++bytesRead;
				++relativePosition;
				--count;
			}
			
			if (relativePosition == 64){
				//write buffer to disk
				io.write_block( 
						getField(getFDAbsoluteIndex(OFT[index].getIndex()), (OFT[index].getPosition() + bytesRead) / 64), 
						OFT[index].getBuffer()
				);
				
				if (nextBlockExists(index, relativePosition) ){
					// read next block
					//OFT[index].setPosition(OFT[index].getPosition() + bytesRead);
					readNextBlockToOFT(index);
				} else if (OFT[index].getPosition() + bytesRead < 2 * blockSize) {
					// allocate new block 
					int newBlockNumber = allocateNewBlockForOFTIndex(index);
					setField(getFDAbsoluteIndex(OFT[index].getIndex()), 
							1 + (OFT[index].getPosition() + bytesRead) / 64,
							newBlockNumber);
				} //else no space for new block
			}
			//continue copying
			relativePosition = 0;
		}
		
		//set new position
		//OFT[index].setPosition(OFT[index].getPosition() + bytesRead);
		return bytesRead;
		
	}
	public int write(int index, byte[] memArea, int count){ //write from memArea to OFT
		if (index < 0 || index > 3) //check bounds
			return -1;
		//if writing to it for first time, alloc file descript block, fill it in
		if (OFT[index].getLength() == 0 && OFT[index].getPosition() == 0){
			int firstBlockNumber = allocateNewBlockForOFTIndex(index);
			setField(getFDAbsoluteIndex(OFT[index].getIndex()), 1, firstBlockNumber);
		}
		int bytesWritten = 0;
		int newBlock = -1;
		int relativePostion = OFT[index].getPosition() % 64; 
		while(count != 0 && (OFT[index].getPosition() + bytesWritten < 3 * blockSize)){
			while(count != 0 && !positionAtEndofBlock(relativePostion) ){
				OFT[index].getBuffer()[relativePostion++] = memArea[0];
				++bytesWritten;
				--count;
				OFT[index].setLength(OFT[index].getLength() + 1);
			}
			//if position is 64, no next block, but space for it, then alloc new block
			if (relativePostion == 64 
					&& !nextBlockExists(index, relativePostion) 
					&& hasSpaceForNextBlock(index)){
				//update file descriptor
				newBlock = allocateNewBlockForOFTIndex(index);
				
				//write filled block to disk
				io.write_block(
						getField(getFDAbsoluteIndex(OFT[index].getIndex()), (OFT[index].getPosition() + bytesWritten) / 64 ), 
						OFT[index].getBuffer()
				);
				
				//clear current OFT index
				Arrays.fill(OFT[index].getBuffer(), (byte) 0);
			} else {
				//write OFT buffer to disk, replace current buffer
				int blockNumber = getField(getFDAbsoluteIndex(OFT[index].getIndex()), (OFT[index].getPosition() + bytesWritten - 1)/64 + 1);
				io.write_block(blockNumber, OFT[index].getBuffer());
			}
			relativePostion = 0;
		}
		
		//update file length, position in OFT and descriptor's length
		OFT[index].setLength( Math.max(OFT[index].getLength(), OFT[index].getPosition() + bytesWritten) );
		OFT[index].setPosition((OFT[index].getPosition() + bytesWritten));
		setField( getFDAbsoluteIndex(OFT[index].getIndex()), 0, OFT[index].getPosition());
		return bytesWritten;
	}
	public int lseek(int index, int newPosition ){
		if (index < 0 || index > OFT.length || OFT[index].isFree()){
			return -1;
		}
		int oldPosition = OFT[index].getPosition();
		//check if new position in current file
		
		//if seeked position is not in same block, load in new block
		if (newPosition / blockSize != oldPosition / blockSize){
		//load new file to OFT
			int 	FDindex = getFDAbsoluteIndex(OFT[index].getIndex()),
					newBlockNumber = getField(FDindex, 1 + newPosition / blockSize);
			
			//write current block
			io.write_block(getField(FDindex, 1 + oldPosition/blockSize), OFT[index].getBuffer());
			//load next block in
			io.read_block(newBlockNumber, OFT[index].getBuffer());
		}
		
		//set new position
		OFT[index].setPosition(newPosition);
		return OFT[index].getPosition();
	}
	public ArrayList<String> directory(){
		ArrayList<String> results = new ArrayList<String>();
		byte [] tempBuffer = new byte [64];
		String temp;
		for (int i = 7; i <10; ++i){
			io.read_block(i, tempBuffer);	
			for (int j = 0; j < blockSize / directoryEntrySize; ++j){
				if (PackableMemory.unpack(tempBuffer, j * directoryEntrySize + 4) > 0){
					temp = new String(Arrays.copyOfRange(tempBuffer, j*directoryEntrySize, j*directoryEntrySize + 4));
					results.add(temp);
				}
			}
		}
		return results;
	}
	public int init(String fileName){
		int status = -1;
		//rewrite oft, cache, and ldisk
		OFT = new OFT_Entry[]{
				new OFT_Entry(7,0), //The Dir, keep it open and at position 0 throughout
				new OFT_Entry(-1,-1),
				new OFT_Entry(-1,-1),
				new OFT_Entry(-1,-1)
		};
		
		//try to read in disk and init cache from those values
		cache = new PackableMemory(numCacheBlocks * blockSize);
		FileInputStream inputFile = null;
		try{
			inputFile = new FileInputStream(fileName);
			if ( inputFile.read(cache.mem) == -1) 
				initCache(); //give it default values if file empty

			//write cache contents to ldisk, one block at a time
			for (int i = 0; i < numCacheBlocks; ++i){
				io.write_block(i, Arrays.copyOfRange(cache.mem, i * blockSize , (i+1) * blockSize) );
			}   
			status = 1;
		} catch (FileNotFoundException fe){
			initCache();
			status = -1;
		} catch (Exception e){
			e.printStackTrace(); 
		}
		finally {
			try {
				//set directory in OFT
				byte [] test = new byte[64];
				io.read_block(7, test);
				OFT[0].setBuffer(test);
				
				if (inputFile != null)
					inputFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return status;
	}
	public void save(String fileName){
		FileOutputStream outputFile = null; 
		try{
			outputFile = new FileOutputStream(fileName);
			//write cache first
			outputFile.write(cache.mem);
			//then write ldisk memory AFTER cache
			byte [] buffer = new byte[blockSize];
			for (int i = numCacheBlocks; i != blockAmount; ++i){
				io.read_block(i, buffer);
				outputFile.write(buffer);
			}
		} catch (Exception e){
			e.printStackTrace();
		} finally{
			try{
				if (outputFile != null)
					outputFile.close();
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}
//random util methods ----------------------------------------------------------- 
	public int getFDIndexByName(String name){
		byte[] nameByte;
		try {
			nameByte = name.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			nameByte = name.getBytes();
		}
		byte [] currentFDEntryBlock = new byte[blockSize];
		byte [] test;
		//search each block of Dir 
		for (int i = 1; i < 4; ++i){
			int currentBlockIndex = getField( 64, i); //we know the dir starts at position 64
			io.read_block(currentBlockIndex, currentFDEntryBlock);
			for (int j = 0; j < blockSize / directoryEntrySize; ++j){ //search through this block of the Dir
				//check if name == memory block
				test = Arrays.copyOfRange(currentFDEntryBlock, j * directoryEntrySize, j * directoryEntrySize + nameByte.length);
				// once name is found, return its FD index
				if (Arrays.equals(test, nameByte)){
					return PackableMemory.unpack(currentFDEntryBlock, j * directoryEntrySize + 4); 
				}
			
			}
		}
		return -1;
	}
	public int getDirIndexByName(String name){
		byte[] nameByte;
		try {
			nameByte = name.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			nameByte = name.getBytes();
		}
		byte [] currentFDEntryBlock = new byte[blockSize];
		byte [] test;
		//search each block of Dir 
		for (int i = 1; i < 4; ++i){
			int currentBlockIndex = getField( 64, i); //we know the dir starts at position 64
			io.read_block(currentBlockIndex, currentFDEntryBlock);
			for (int j = 0; j < blockSize / directoryEntrySize; ++j){ //search through this block of the Dir
				//check if name == memory block
				test = Arrays.copyOfRange(currentFDEntryBlock, j * directoryEntrySize, j * directoryEntrySize + nameByte.length);
				// once name is found, return its FD index
				if (Arrays.equals(test, nameByte)){
					return i * 8 + j; //give number from 0 to 23 inclusive
				}
			
			}
		}
		return -1;
	}
	public int allocateNewBlockForOFTIndex(int OFTindex){
		int freeBMIndex = findFreeBlockBitMap();
		if (freeBMIndex == -1)
			return -1;
		
		//set value in file descriptor
		int FDabsIndex = getFDAbsoluteIndex(OFT[OFTindex].getIndex());
		for (int i = 0; i < 3; ++i){ // 0, 1, 2 are indices after the length field
			if (cache.unpack(FDabsIndex + ((i + 1) * 4)) == -1){
				cache.pack(freeBMIndex, FDabsIndex + ((i + 1) * 4));
				//setField(FDabsIndex, i + 1, freeBMIndex);
				return freeBMIndex;
			}
		}
		
		return -1;
		// this is how we dod it
	}
	public int findFreeBlockBitMap(){
		//search bm for free index, else, return false 
		int test = 0;
		for (int i = 0; i < 8; ++i){ //byte num
			for (int j = 0; j < 8; ++j){ //bit num 
				//test = cache.mem[i * 8 + j] & (1 << (7 - j) ); //bitmap is left to right
				test = getBM(i * 8 + j);
				if (test == 0){
					//set this new index using lecture notes
					//cache.mem[i] |= (1 << (7 - j));
					setBM(i * 8 + j, 1);
//					.println("After");
//					FileSystem.printBinStr(cache.mem, 8);
					return i * 8 + j;
				}
			}
		}
		return -1;
	}
	public void setBM(int index, int value){
		int 	i = index / 8,
				j = index % 8;
		if (value == 0)
			cache.mem[i] &= ~(1 << (7 - j));	//set to zero
		else
			cache.mem[i] |= (1 << (7 - j));		//set to one
	}
	public int getBM(int index){
		int 	i = index / 8,
				j = index % 8;
		return cache.mem[i] & (1 << (7 - j));
	}
	public int setField(int absoluteFDindex, int field, int value){
		int oldValue = cache.unpack(absoluteFDindex + ((field) * 4));
		cache.pack(value, absoluteFDindex + ((field) * 4));
		return oldValue;
	}
	public int getField(int absoluteFDindex, int field){
		return cache.unpack(absoluteFDindex + ((field) * 4));
	}
	public boolean nextBlockExists(int OFTindex, int currentPosition){
		int FDindex = OFT[OFTindex].getIndex();
		int currentBlock = currentPosition / blockSize;
		return cache.unpack(getFDAbsoluteIndex(FDindex) + ((currentBlock + 1) * 4)) != -1;
	}
	public boolean hasSpaceForNextBlock(int OFTindex){
		//if the file length < 2 block sizes, then we can always allocate one more
		return OFT[OFTindex].getLength() <= 2 * blockSize;
	}
	public boolean positionAtEndofBlock(int position){
		//return (position != 0) && (position < blockSize);
		//return (position != 0 && (position + 1) % blockSize == 0);
		return (position != 0 && (position % blockSize == 0));
	}
	public int findFreeDescriptor(){ //returns index on cache where a free one  exists
		int fileDescriptorIndex = -1;
		
		for (int i = 1; i != numCacheBlocks; ++i){ //1 - 6
			//linear search for a free file descriptor 
			for (int j = 0; j != blockSize/fileDescriptorSize; ++j){ // 0 - 3
				if (i == 1 && j == 0){ //skip file descriptor
					continue;
				}
				//read in each file descriptor, one by one until a free one is found
				if ( cache.unpack(i*blockSize + j*fileDescriptorSize) < 0){ //just change this here to extract each field
					fileDescriptorIndex = i*blockSize + j*fileDescriptorSize;
					return fileDescriptorIndex;
				}
			}
		}
		return -1;
	}
	public int getFreeDirEntry(){
		//Returns a relative index for a free dir entry - blocks should be 7,8,9
		io.write_block(7 + OFT[0].getPosition() / 64, OFT[0].getBuffer());
		for (int i = 0; i < 3; ++i){
			io.read_block(7 + i, OFT[0].getBuffer()); //get new block each time;
			for (int j = 0; j < blockSize/directoryEntrySize; ++j){
				int currentDirEntryIndex = j * directoryEntrySize;
				//look at index position
				int currentDirEntryIndexValue = PackableMemory.unpack(OFT[0].getBuffer(), currentDirEntryIndex);
				if (currentDirEntryIndexValue == 0){
					OFT[0].setPosition(currentDirEntryIndex + directoryEntrySize);
					OFT[0].setLength(OFT[0].getLength() + directoryEntrySize);
					return currentDirEntryIndex;
				}
			}
		}
		return -1;
	}
	public int getFDAbsoluteIndex(int FDRelativeIndex){
		return blockSize + FDRelativeIndex * directoryEntrySize;
	}
	public int getFDRelativeIndex(int FDAbsoluteIndex){
		return  (FDAbsoluteIndex - blockSize) / directoryEntrySize;
	}
	public boolean readNextBlockToOFT(int oftIndex){
		int position = OFT[oftIndex].getPosition();
		if (position >= 2 * blockSize) // if third block already, cant read more
			return false;
		
		//get next block from file descriptors
		int fdIndex = OFT[oftIndex].getIndex();
		int fdOffset = position / blockSize; //0-63->0, 64-126->1
		//int dataBlockIndex = cache.unpack((blockSize + fdIndex * fileDescriptorSize) + (fdOffset + 1) * 4);
		int dataBlockIndex = getField(getFDAbsoluteIndex(OFT[oftIndex].getIndex()), ((position - 1) / 64) + 2);
		//then assign next block into oft
		io.read_block(dataBlockIndex, OFT[oftIndex].getBuffer()); 
		
		return true;
	}
	public void initCache(){
		//init bitmap to reserve first 10 blocks bitmap(1) + filedescriptors(6) + dirBlocks(3)
		for (int i = 0; i < 10; ++i){
			this.setBM(i, 1);
		}
		//reserve blocks 7 - 9 for Directory
		for (int i = 7; i < 10; ++i)
			setField(blockSize, i - 6, i); // set fields 1, 2, 3
		
		Arrays.fill(cache.mem, blockSize + fileDescriptorSize, cache.mem.length, (byte)-1); //fill all w/ -1??
		
	}
	public static void printBinStr(byte[] bytes, int numberLines){
		int i = 0;
		for (byte b : bytes) {
			if (i == numberLines)
				return;
			System.out.format("%d: %s\n", i++, Integer.toBinaryString(b & 255 | 256).substring(1));
		}
	}
}