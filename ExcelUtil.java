package com.xinge.yijia.server.common.utils;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelUtil {

    public static <T extends Map<String, List<V>>, V> void write(T t, Class<V> classOfV, String path) throws Exception {
        HSSFWorkbook workbook = new HSSFWorkbook();

        for (Map.Entry<String, List<V>> entry : t.entrySet()) {
            HSSFSheet sheet = workbook.createSheet(entry.getKey());

            int rowCount = 0;
            for (V v : entry.getValue()) {
                int column = 0;
                HSSFRow row = sheet.createRow(rowCount);
                Map<String, String> content = ObjectMergeUtils.mergeObject(classOfV, v, true);
                for (Map.Entry<String, String> item : content.entrySet()) {
                    HSSFCell cell = row.createCell(column);
                    cell.setCellValue(item.getValue());
                    column++;
                }
                rowCount++;
            }
        }

        FileOutputStream fos = new FileOutputStream(path);
        workbook.write(fos);
        System.out.println("写入成功" + path);
        fos.close();
    }

  /*  public static void main(String[] args) throws Exception {
        Map<String, List<WXRefund>> map = new HashMap<>();
        List<WXRefund> wxRefunds = new ArrayList<>();
        map.put("退款", wxRefunds);
        for(int i = 0; i < 10; i++){
            WXRefund wxRefund = new WXRefund();
            wxRefunds.add(wxRefund);
            wxRefund.setRefundAccount("123123");
            wxRefund.setRefundDesc("123123");
            wxRefund.setRefundId("asdfasdf");
            wxRefund.setRefundMoney(12312);
            wxRefund.setRefundMoneyType("中文中水水水水");
        }

        write(map, WXRefund.class, "e:/asdf.xls");
    }
*/

    public static <T extends Map<String, List<V>>, V> XSSFWorkbook exportFile(T t, Class<V> classOfV) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFCellStyle style = workbook.createCellStyle();
        for (Map.Entry<String, List<V>> entry : t.entrySet()) {
            XSSFSheet sheet = workbook.createSheet(entry.getKey());
            int rowCount = 0;
            for (V v : entry.getValue()) {
                int column = 0;
                XSSFRow row = sheet.createRow(rowCount);
                style.setBorderBottom(BorderStyle.THIN); //下边框
                style.setBorderLeft(BorderStyle.THIN);//左边框
                style.setBorderTop(BorderStyle.THIN);//上边框
                style.setBorderRight(BorderStyle.THIN);//右边框
                Map<String, String> content = ObjectMergeUtils.mergeObject(classOfV, v, true);
                for (Map.Entry<String, String> item : content.entrySet()) {
                    XSSFCell cell = row.createCell(column);
                    cell.setCellValue(item.getValue());
                    cell.setCellStyle(style);
                    column++;
                }
                rowCount++;
            }
        }
        System.out.println("文件写入成功");
        return workbook;
    }
}

    <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi</artifactId>
            <version>3.17</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>3.17</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml-schemas</artifactId>
            <version>3.17</version>
        </dependency>
