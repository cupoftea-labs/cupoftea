import com.datafrog.DataFrame;
import junit.framework.TestCase;

import java.util.ArrayList;

public class DataFrameTest extends TestCase {

    public void testAddColumn(){
        String column = "Coluna 1";

        DataFrame df = new DataFrame();
        df.addColumn(column);

        ArrayList<String> espected = new ArrayList<>();
        espected.add(column);

        assertEquals(espected, df.getHeaders());
    }

    public void testRemoveColumnByName(){

    }

    public void testRemoveColumnByIndex(){

    }

    public void testAddRow(){

    }

    public void testImportCsv(){


    }

}
