package il.co.codeguru.corewars8086.war;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import il.co.codeguru.corewars8086.cli.Options;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WarriorRepositoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(String name, int... bytes) throws Exception {
        File f = tmp.newFile(name);
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        Files.write(f.toPath(), data);
        return f;
    }

    private WarriorRepository load() throws Exception {
        WarriorRepository repo = new WarriorRepository(false, new Options());
        repo.readWarriorsFileFromPath(tmp.getRoot().getPath());
        return repo;
    }

    @Test
    public void groupsPairedAndSingleSurvivors() throws Exception {
        write("bimp1", 1);
        write("bimp2", 2);
        write("shooter", 3);

        WarriorRepository repo = load();

        assertArrayEquals(new String[] {"bimp", "shooter"}, repo.getGroupNames());
        assertEquals(2, repo.getWarriorGroups().get(0).getWarriors().size());
    }

    @Test
    public void neverDeletesOrRenamesFiles() throws Exception {
        write("a");
        File source = write("a.asm", 9);
        File notes = write("notes.txt", 9);
        File bin = write("b.bin", 7);

        load();

        assertTrue(source.exists());
        assertTrue(notes.exists());
        assertTrue(bin.exists());
    }

    @Test
    public void binFileIsLoadedUnderItsBaseNameAndWinsOverPlainFile() throws Exception {
        write("w", 1);
        write("w.bin", 5, 6);
        write("ignored.asm", 1);

        WarriorRepository repo = load();

        assertArrayEquals(new String[] {"w"}, repo.getGroupNames());
        WarriorData data = repo.getWarriorGroups().get(0).getWarriors().get(0);
        assertEquals("w", data.getName());
        assertArrayEquals(new byte[] {5, 6}, data.getCode());
    }

    @Test(expected = FileNotFoundException.class)
    public void missingDirectoryIsReported() throws Exception {
        new WarriorRepository(false, new Options())
            .readWarriorsFileFromPath(new File(tmp.getRoot(), "nope").getPath());
    }

    @Test(expected = java.io.IOException.class)
    public void secondHalfWithoutFirstIsReported() throws Exception {
        write("lonely2", 1);
        load();
    }
}
