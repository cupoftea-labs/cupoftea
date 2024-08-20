import junit.framework.TestCase;

import java.util.ArrayList;

import com.CupOfTea.CupOfTea;

public class DataFrameTest extends TestCase {

    public void testAddColumn() throws Exception{
        String column = "Coluna 1";

        CupOfTea df = new CupOfTea();
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
