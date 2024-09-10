package com.CupOfTea.importers;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.CupOfTea.CupOfTea;
import com.CupOfTea.importers.interfaces.IExcelImporter;
import com.CupOfTea.table.Column;

public class ExcelImporter implements IExcelImporter{

    private final CupOfTea dataFrame;

    public ExcelImporter(final CupOfTea dataFrame){
        this.dataFrame = dataFrame;
    }


    /**
     * Dumps the EXCEL data into a CupOfTea data frame.
     * 
     * This method reads the first Worksheet and start reading from A0.
     * 
     * @param file path of the EXCEL file. Only accept .xls .xlsx and .xlsm file extentions. Can't be null.
     */
    @Override
    public void importFromExcel(File file, boolean hasHeaders) throws Exception {
        // todo poder escolher em qual celula comeca a tabela

        String filename = file.toString();
        Optional<String> fileExtention = Optional.ofNullable(filename)
                                        .filter(f -> f.contains("."))
                                        .map(f -> f.substring(filename.lastIndexOf(".") + 1));

        if(!fileExtention.isPresent()) throw new Exception("Not a Excel File");

        
        if(fileExtention.get().equals("xls")){
            FileInputStream fis=new FileInputStream(file);  
            try (HSSFWorkbook wb = new HSSFWorkbook(fis)) {
                HSSFSheet sheet=wb.getSheetAt(0);  // Todo poder escolher qual a sheet do arquivo excel que está a tablea
                FormulaEvaluator formulaEvaluator=wb.getCreationHelper().createFormulaEvaluator();  
                boolean firstRow = true;
                HashMap<String, Object> rowDF = new HashMap<>();
                for(Row row: sheet) {  
                    int i = 0; // Todo Mudar para usar um iterator dos headers
                    for(Cell cell: row) {  
                        if(firstRow && hasHeaders) {
                                switch (formulaEvaluator.evaluateInCell(cell).getCellType()) {  
                                    case STRING:
                                        this.dataFrame.getHeaders().add(new Column(cell.getStringCellValue()));
                                    break;  
                                    case NUMERIC:
                                        this.dataFrame.getHeaders().add(new Column(String.valueOf(cell.getNumericCellValue())));
                                    break;
                                    case BLANK:
                                    case BOOLEAN:
                                    case ERROR:
                                    case FORMULA:
                                    case _NONE:
                                    default:
                                        this.dataFrame.getHeaders().add(null);  
                                        break;    
                                }  
                        } else {
                            switch(formulaEvaluator.evaluateInCell(cell).getCellType()) {  
                                case STRING:
                                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(), cell.getStringCellValue());
                                break;  
                                case NUMERIC:
                                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(), cell.getNumericCellValue());
                                break;
                                case BLANK:
                                case BOOLEAN:
                                case ERROR:
                                case FORMULA:
                                case _NONE:
                                default:
                                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(), null);
                                    break;  
                            }
                            i++;
                        }
                    } 
                    if(!firstRow || !hasHeaders) this.dataFrame.addRow(rowDF);
                    firstRow = false;     
                }
            }  
        }

        if(fileExtention.get().equals("xlsx")){
            FileInputStream fis = new FileInputStream(file);
            try (XSSFWorkbook wb = new XSSFWorkbook(fis)) {
                XSSFSheet sheet = wb.getSheetAt(0);
                Iterator<Row> itr = sheet.iterator();

                boolean firstRow = true;
                while (itr.hasNext() ) {  
                    Row row = itr.next();  
                    Iterator<Cell> cellIterator = row.cellIterator();
                    int i = 0;
                    HashMap<String, Object> rowDF = new HashMap<>();
                    while (cellIterator.hasNext()) {  
                        Cell cell = cellIterator.next();  
                        
                        if(firstRow && hasHeaders) {
                            switch (cell.getCellType()) {  
                                case STRING:
                                    this.dataFrame.getHeaders().add(new Column(cell.getStringCellValue()));
                                break;  
                                case NUMERIC:
                                    this.dataFrame.getHeaders().add(new Column(String.valueOf(cell.getNumericCellValue())));
                                break;
                                case BLANK:
                                case BOOLEAN:
                                case ERROR:
                                case FORMULA:
                                case _NONE:
                                default:
                                    this.dataFrame.getHeaders().add(null);  
                                break;
                            }  
                        } else {
                            switch (cell.getCellType()) {  
                                case STRING:
                                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(), cell.getStringCellValue());
                                break;  
                                case NUMERIC:
                                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(), cell.getNumericCellValue());
                                break;
                                case BLANK:
                                case BOOLEAN:
                                case ERROR:
                                case FORMULA:
                                case _NONE:
                                default:
                                    rowDF.put(this.dataFrame.getHeaders().get(i).columnName(), null);
                                break; 
                            }  
                            i++;
                        }
                        
                    } 
                    if(!firstRow || !hasHeaders) this.dataFrame.addRow(rowDF);
                    firstRow = false; 
                }
            } 
        }
    }

}
