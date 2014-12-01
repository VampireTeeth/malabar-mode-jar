import static net.java.quickcheck.generator.PrimitiveGenerators.*
import static net.java.quickcheck.generator.CombinedGenerators.*
import static net.java.quickcheck.QuickCheck.*
import static net.java.quickcheck.generator.iterable.Iterables.*

import net.java.quickcheck.Generator;
import net.java.quickcheck.StatefulGenerator;

import org.junit.Test;

import static org.junit.Assert.*

import com.software_ninja.malabar.project.MavenProjectHandler;
import groovy.io.FileType

class MavenProjectTester {



  def elementGenerator (coll) {
    return new StatefulGenerator<File>() {

      private Generator<Integer> ints = integers(0, coll.size() - 1);

      public void reset () {
	ints = PrimitiveGenerators.integers(0, coll.size() - 1);
      }

      public File next () {
	int i = ints.next();
	println i + " " + coll.size();
	return coll[i];
      }
    }
  }
  
  /**
   * Generator of pom files. See src/test/resources/pom.
   *
   * Returns a generator that returns the list of pom files.
   **/
  
  def pomGenerator (rootPath, pattern) { 
    List<File> files = [];
    File root = new File(rootPath);
    root.eachFileMatch(pattern) {
      f -> files << f;
    }
    root.eachFileRecurse(FileType.DIRECTORIES) {
      dir -> dir.eachFileMatch(pattern) {
	f -> files << f;
      }
    }

    return elementGenerator(files);
    
  }
  
  
  /**
   * Properties:
   *  # Not throw an exception
   *  # Returns a map
   *  # the map has a runtime key
   *  # the map has a test key
   **/
  @Test
  void testMavenImport() {
    for (File pom : toIterable( pomGenerator("src/test/resources/pom/",~/.*\.pom/ ))) {
      println "processing " + pom;
      Map map = new MavenProjectHandler().projectInfo( System.getProperty("user.home") +  "/.m2/repository",
						       pom.absolutePath);
      assertNotNull( map['test']);
      assertNotNull( map['runtime']);
    }
  }
}