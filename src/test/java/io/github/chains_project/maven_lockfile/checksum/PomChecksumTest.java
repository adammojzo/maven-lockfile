package io.github.chains_project.maven_lockfile.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link AbstractChecksumCalculator#calculatePomChecksum}, exercised through {@link FakeChecksumCalculator}. */
class PomChecksumTest {

    @TempDir
    Path dir;

    @Test
    void checksumIsLowercaseHexSha256() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), "abc");

        // SHA-256("abc") from FIPS 180-2
        assertThat(new FakeChecksumCalculator().calculatePomChecksum(pom))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void lineEndingsAreNormalizedBeforeHashing() throws Exception {
        Path unix = Files.writeString(dir.resolve("unix.xml"), "<project>\n</project>\n");
        Path windows = Files.writeString(dir.resolve("windows.xml"), "<project>\r\n</project>\r\n");
        Path oldMac = Files.writeString(dir.resolve("mac.xml"), "<project>\r</project>\r");

        var calc = new FakeChecksumCalculator();

        assertThat(calc.calculatePomChecksum(windows)).isEqualTo(calc.calculatePomChecksum(unix));
        assertThat(calc.calculatePomChecksum(oldMac)).isEqualTo(calc.calculatePomChecksum(unix));
    }

    @Test
    void missingFileYieldsEmptyChecksum() {
        assertThat(new FakeChecksumCalculator().calculatePomChecksum(dir.resolve("does-not-exist.xml")))
                .isEmpty();
    }
}
