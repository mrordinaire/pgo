package pgo.modules;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import pgo.model.tla.TLAModule;
import pgo.parser.ParseFailureException;

public class TLAModuleLoaderTest {

	@Test
	public void testModuleNotFound() throws IOException, ParseFailureException, NoModulesFoundInFileError {
		TLAModuleLoader loader = new TLAModuleLoader(Collections.emptyList());
		try {
			loader.loadModule("Test");
			fail("should have thrown ModuleLoadError");
		}catch(ModuleNotFoundError ex) {
			assertThat(ex.getPathsChecked(), is(Collections.emptyList()));
		}
	}
	
	@Test
	public void testModuleFoundOneOption() throws ModuleNotFoundError, IOException, ParseFailureException, NoModulesFoundInFileError {
		TLAModuleLoader loader = new TLAModuleLoader(Collections.singletonList(Paths.get("test", "pluscal")));
		
		TLAModule m = loader.loadModule("Sum");
		assertThat(m.getName().getId(), is("Sum"));
	}
	
	@Test
	public void testModuleFoundFailOver() throws ModuleNotFoundError, IOException, ParseFailureException, NoModulesFoundInFileError {
		TLAModuleLoader loader = new TLAModuleLoader(Arrays.asList(Paths.get("test", "tla", "tokens"),
				Paths.get("test", "pluscal")));
		
		TLAModule m = loader.loadModule("Sum");
		assertThat(m.getName().getId(), is("Sum"));
	}

}
