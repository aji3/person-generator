package io.github.aji3.persongenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xlbean.XlBean;
import org.xlbean.definition.BeanDefinitionLoader;
import org.xlbean.reader.XlBeanReader;
import org.xlbean.util.XlBeanFactory;
import org.xlbean.writer.XlBeanWriter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PersonGeneratorMain {

    private static Logger log = LoggerFactory.getLogger(PersonGeneratorMain.class);

    public static void main(String[] args) {
        new PersonGeneratorMain().run();
    }

    private String excelFileName = "person_generator.xlsx";
    private XlBean xlbean;

    private void init() {
        XlBeanFactory.setInstance(new FlexibleXlBeanFactory());

        log.info("Start loading excel {}", excelFileName);
        xlbean = new XlBeanReader().read(new File(excelFileName));
        log.info("End loading excel {}", excelFileName);
    }

    public void run() {

        String now = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());

        init();

        PersonGenerator generator = new PersonGenerator(xlbean);

        List<XlBean> resultList = new ArrayList<>();

        int numberToGenerate = 10;
        try {
            numberToGenerate = Integer.parseInt(xlbean.value("numberToGenerate"));
        } catch (NumberFormatException e) {
            System.err.println("Illegal number format for numberToGenerate field. Default number 10 is used.");
        }
        log.info("Start generating {} data.", numberToGenerate);

        String targetType = xlbean.value("targetType");

        for (int i = 0; i < numberToGenerate; i++) {
            log.info("Start {}", i);

            resultList.addAll(generator.generate(targetType));

            log.info("End {}", i);
        }

        log.info("Start output");

        writeToExcel(resultList, now);
        writeToJson(resultList, now);
        log.info("End output");
    }

    private void writeToExcel(List<XlBean> resultList, String executedTimestamp) {
        XlBean output = new XlBean();
        output.set("persons", resultList);
        XlBeanWriter writer = new XlBeanWriter(new BeanDefinitionLoader(2));
        String outExcelFileName = String.format("result_%s.xlsx", executedTimestamp);
        try (OutputStream outFile = new FileOutputStream(outExcelFileName)) {
            writer.write(output, null, output, outFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Result saved to excel file: {}", outExcelFileName);
    }

    private void writeToJson(List<XlBean> resultList, String executedTimestamp) {
        String outJsonFileName = String.format("result_%s.json", executedTimestamp);
        ObjectMapper mapper = new ObjectMapper();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outJsonFileName), "UTF-8");) {
            mapper.writeValue(writer, resultList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Result in JSON format saved to {}", outJsonFileName);
    }

}
