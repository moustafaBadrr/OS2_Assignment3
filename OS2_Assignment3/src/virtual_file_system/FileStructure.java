package virtual_file_system;

import data_structures.MyBlock;
import data_structures.MyDirectory;
import data_structures.MyFile;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class FileStructure implements Serializable {

    private final Disk disk;
    private final MyDirectory root;
    private final List<Boolean> bitMap;
    private final List<MyBlock> blocks;
    private final String allocationAlgorithm;

    public static final String CONTIGUOUS = "Contiguous Allocation.";
    public static final String INDEXED = "Indexed Allocation.";

    public FileStructure(int numberOfBlocks, String allocationAlgorithm) {
        disk = new Disk(numberOfBlocks);
        root = new MyDirectory("root");

        bitMap = new ArrayList<>(numberOfBlocks);
        for (int i = 0; i < numberOfBlocks; i++) {
            bitMap.add(false);
        }

        blocks = new ArrayList<>(numberOfBlocks);
        for (int i = 0; i < numberOfBlocks; i++) {
            blocks.add(new MyBlock(i));
        }

        this.allocationAlgorithm = allocationAlgorithm;

    }

    public boolean createFile(MyFile myFile, int fileSize) {
        if (allocationAlgorithm.equals(CONTIGUOUS)) {
            return createFileCont(myFile, fileSize);
        } else {
            return createFileIndexed(myFile, fileSize);
        }
    }

    public boolean createFolder(MyDirectory myDirectory) {
        String path = myDirectory.getPath();
        String patharr[] = path.split("/");
        String lastfolder = "root";
        for (int i = 1; i < patharr.length - 1; i++) {
            lastfolder += "/" + patharr[i];
        }
        MyDirectory pointer = findDirectory(root, lastfolder);
        if (pointer != null) {
            for (int i = 0; i < pointer.getDirectories().size(); i++) {
                if (path.equals(pointer.getDirectories().get(i).getPath())) {
                    return false;
                }
            }
            pointer.addDirectory(myDirectory);
            return true;
        } else {
            return false;
        }
    }

    public boolean deleteFile(MyFile myFile) {
        if (allocationAlgorithm.equals(CONTIGUOUS)) {
            return deleteFileCont(myFile);
        } else {
            return deleteFileIndexed(myFile);
        }
    }

    public boolean deleteFolder(MyDirectory myDirectory) {
        String path = myDirectory.getPath();
        String patharr[] = path.split("/");
        String parentFolder = "root";
        for (int i = 1; i < patharr.length - 1; i++) {
            parentFolder += "/" + patharr[i];
        }
        MyDirectory parentFolderPointer = findDirectory(root, parentFolder);
        if (parentFolderPointer == null) {
            return false;
        }
        MyDirectory folderpointer = null;
        for (int i = 0; i < parentFolderPointer.getDirectories().size(); i++) {
            if (path.equals(parentFolderPointer.getDirectories().get(i).getPath())) {
                folderpointer = parentFolderPointer.getDirectories().get(i);
            }
        }
        if (folderpointer == null) {
            return false;
        }
        deleteFolderUtil(parentFolderPointer, folderpointer, allocationAlgorithm);
        return true;
    }

    public void displayFileStrucutre() {

        Queue<MyDirectory> queue = new LinkedList<>();
        queue.add(this.root);

        while (!queue.isEmpty()) {
            List<MyDirectory> currentLevel = new ArrayList<MyDirectory>();
            currentLevel.addAll(queue);
            queue.clear();

            for (MyDirectory myDirectory : currentLevel) {

                System.out.print("<" + myDirectory.getPath() + "> ");

                for (int i = 0; i < myDirectory.getFiles().size(); i++) {

                    System.out.print(myDirectory.getFiles().get(i).getPath() + " ");
                }

                for (int i = 0; i < myDirectory.getDirectories().size(); i++) {
                    queue.add(myDirectory.getDirectories().get(i));
                }

            }

            System.out.println();
        }

    }

    public void displayDiskStatus() {
        disk.displayDisk();
    }

    public boolean checkFileExistence(MyDirectory pointer, MyFile myFile) {
        for (int i = 0; i < pointer.getFiles().size(); i++) {
            if (pointer.getFiles().get(i).getPath().equals(myFile.getPath())) {
                return false;
            }
        }
        return true;
    }

    private boolean createFileCont(MyFile myFile, int fileSize) {
        /*
         * Pre-requests:
         1- The path is already exist 
         2- No file with the same name is already created under this path 
         3- Enough space exists
         */

        // Check if there is an enough space for the file
        boolean foundStart = false, enoughSpace = false;
        int startIndex = -1, foundSize = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (foundStart == false && bitMap.get(i).equals(false)) {
                foundStart = true;
                startIndex = i;
                foundSize += 1;
            } else if (bitMap.get(i).equals(false)) {
                foundSize += 1;
            } else {
                startIndex = -1;
                foundSize = 0;
            }
            if (foundSize == fileSize) {
                enoughSpace = true;
                break;
            }
        }

        // Check if the path exists and if the file exists
        String[] pathArray = myFile.getPath().split("/");
        String lastfolder = "root";
        boolean pathExists = false, fileNotExists = true;
        for (int i = 1; i < pathArray.length - 1; i++) {
            lastfolder += "/" + pathArray[i];
        }
        MyDirectory pointer = findDirectory(root, lastfolder);
        if (pointer != null) {
            pathExists = true;
            fileNotExists = checkFileExistence(pointer, myFile);
        }

        // if the Pre-requests are true
        if (enoughSpace && pathExists && fileNotExists) {
            myFile.setAllocatedBlock(startIndex);
            List<Integer> Data = new ArrayList<Integer>();
            Data.add(fileSize);
            bitMap.set(startIndex, true);
            blocks.get(startIndex).setData(Data);
            blocks.get(startIndex).setIndex(startIndex);
            for (int i = startIndex + 1; i < fileSize + startIndex; i++) {
                bitMap.set(i, true);
            }
            pointer.addFile(myFile);
            disk.addToAllocatedBlocks(fileSize);
            disk.addToAllocatedSpace(fileSize);
        } else {
            return false;
        }

        return true;
    }

    private boolean createFileIndexed(MyFile myFile, int fileSize) {
        /*
         * Pre-requests: 
        1- The path is already exist 
        2- No file with the same name is already created under this path 
        3- Enough space exists
         */

        // Check if there is an enough space for the file
        boolean enoughSpace = false;
        int startIndex = -1, foundSize = 0;
        List<Integer> Indexes = new ArrayList<Integer>();
        for (int i = 0; i < blocks.size(); i++) {
            if (startIndex == -1 && bitMap.get(i).equals(false)) {
                startIndex = i;
            } else if (bitMap.get(i).equals(false)) {
                foundSize += 1;
                Indexes.add(i);
            }
            if (foundSize == fileSize) {
                enoughSpace = true;
                break;
            }
        }

        // Check if the path exists and if the file exists
        String[] pathArray = myFile.getPath().split("/");
        String lastfolder = "root";
        boolean pathExists = false, fileNotExists = true;
        for (int i = 1; i < pathArray.length - 1; i++) {
            lastfolder += "/" + pathArray[i];
        }
        MyDirectory pointer = findDirectory(root, lastfolder);
        if (pointer != null) {
            pathExists = true;
            fileNotExists = checkFileExistence(pointer, myFile);
        }

        if (enoughSpace && pathExists && fileNotExists) {
            myFile.setAllocatedBlock(startIndex);
            bitMap.set(startIndex, true);
            blocks.get(startIndex).setData(Indexes);

            for (int i = 0; i < Indexes.size(); i++) {
                bitMap.set(Indexes.get(i), true);
            }
            pointer.addFile(myFile);
            disk.addToAllocatedBlocks(fileSize + 1);
            disk.addToAllocatedSpace(fileSize + 1);
        } else {
            return false;
        }

        return true;
    }

    private MyDirectory findDirectory(MyDirectory root, String lastfolder) {
        if (root.getPath().equals(lastfolder)) {
            return root;
        }
        if (root.getDirectories().isEmpty()) {
            return null;
        }

        for (int i = 0; i < root.getDirectories().size(); i++) {
            MyDirectory d = findDirectory(root.getDirectories().get(i), lastfolder);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private boolean deleteFileCont(MyFile myFile) {
        // Check if the path exists and if the file exists
        String[] pathArray = myFile.getPath().split("/");
        String lastfolder = "root";
        boolean pathExists = false, fileNotExists = true;
        for (int i = 1; i < pathArray.length - 1; i++) {
            lastfolder += "/" + pathArray[i];
        }
        MyDirectory pointer = findDirectory(root, lastfolder);
        if (pointer != null) {
            pathExists = true;
            fileNotExists = checkFileExistence(pointer, myFile);
        }

        int index = -1;
        int startIndex = -1, fileSize = 0;
        List<Integer> data = new ArrayList<Integer>();
        if (pathExists && !fileNotExists) {
            for (int i = 0; i < pointer.getFiles().size(); i++) {
                if (pointer.getFiles().get(i).getPath().equals(myFile.getPath())) {
                    index = i;
                    break;
                }
            }
            myFile = pointer.getFiles().get(index);
            startIndex = myFile.getAllocatedBlock();
            data = blocks.get(startIndex).getData();
            fileSize = data.get(0);
            for (int i = startIndex; i < fileSize + startIndex; i++) {
                bitMap.set(i, false);
            }
            pointer.removeFile(index);
            disk.subtractAllocatedSpace(fileSize);
            disk.subtractAllocatedBlocks(fileSize);
        } else {
            return false;
        }
        return true;
    }

    private boolean deleteFileIndexed(MyFile myFile) {

        // Check if the path exists and if the file exists
        String[] pathArray = myFile.getPath().split("/");
        String lastfolder = "root";
        boolean pathExists = false, fileNotExists = true;
        for (int i = 1; i < pathArray.length - 1; i++) {
            lastfolder += "/" + pathArray[i];
        }
        MyDirectory pointer = findDirectory(root, lastfolder);
        if (pointer != null) {
            pathExists = true;
            fileNotExists = checkFileExistence(pointer, myFile);
        }

        int fileIndex = -1;
        int startIndex = -1;
        List<Integer> data = new ArrayList<Integer>();

        if (pathExists && !fileNotExists) {
            for (int i = 0; i < pointer.getFiles().size(); i++) {
                if (pointer.getFiles().get(i).getPath().equals(myFile.getPath())) {
                    fileIndex = i;
                    break;
                }
            }
            myFile = pointer.getFiles().get(fileIndex);
            startIndex = myFile.getAllocatedBlock();
            data = blocks.get(startIndex).getData();
            for (int i = 0; i < data.size(); i++) {
                bitMap.set(data.get(i), false);
            }
            pointer.removeFile(fileIndex);
            int size = data.size() + 1;
            disk.subtractAllocatedSpace(size);
            disk.subtractAllocatedBlocks(size);
        } else {
            return false;
        }
        return true;
    }

    private void deleteFolderUtil(MyDirectory root, MyDirectory deletedFolder, String type) {

        while(!deletedFolder.getFiles().isEmpty()){
            if (type.equals(FileStructure.CONTIGUOUS)) {
                deleteFileCont(deletedFolder.getFiles().get(0));
            } else {
                deleteFileIndexed(deletedFolder.getFiles().get(0));
            }
        }
        while(!deletedFolder.getDirectories().isEmpty()){
            deleteFolderUtil(deletedFolder, deletedFolder.getDirectories().get(0), type);
        }
        root.getDirectories().remove(deletedFolder);
    }
}
