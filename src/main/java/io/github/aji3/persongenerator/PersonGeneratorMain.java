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

import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xlbean.XlBean;
import org.xlbean.definition.BeanDefinitionLoader;
import org.xlbean.reader.XlBeanReader;
import org.xlbean.util.FieldAccessHelper;
import org.xlbean.util.XlBeanFactory;
import org.xlbean.writer.XlBeanWriter;

import com.fasterxml.jackson.databind.ObjectMapper;

import groovy.lang.GroovyShell;

public class PersonGeneratorMain {

    private static Logger log = LoggerFactory.getLogger(PersonGeneratorMain.class);

    public static void main(String[] args) {
        new PersonGeneratorMain().run();
    }

    private String excelFileName = "person_generator.xlsx";
    private GroovyShell shell;
    private XlBean xlbean;

    public void run() {

        String now = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());

        init();

        List<XlBean> resultList = new ArrayList<>();

        int numberToGenerate = 10;
        try {
            numberToGenerate = Integer.parseInt(xlbean.value("numberToGenerate"));
        } catch (NumberFormatException e) {
            System.err.println("Illegal number format for numberToGenerate field. Default number 10 is used.");
        }
        log.info("Start generating {} data.", numberToGenerate);

        for (int i = 0; i < numberToGenerate; i++) {
            log.info("Start {}", i);
            for (XlBean instanceType : xlbean.list("instanceTypes")) {
                log.info("Start generate {}", i);
                String instanceTypeName = instanceType.value("name");
                XlBean target = generateBlankInstance(instanceTypeName);
                if (evaluateGenerateCondition(target, resultList, instanceType)) {
                    log.info("Start generate {} {}", i, instanceTypeName);
                    runGeneratorAndPopulateTarget(target, resultList);
                    resultList.add(target);
                    log.info("End generate {} {}", i, instanceTypeName);
                } else {
                    log.info("Skipped {} {}", i, instanceTypeName);
                }
            }
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

    private void init() {
        XlBeanFactory.setInstance(new FlexibleXlBeanFactory());

        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass("io.github.aji3.persongenerator.GeneratorDsl");
        shell = new GroovyShell(config);

        log.info("Start loading excel {}", excelFileName);
        xlbean = new XlBeanReader().read(new File(excelFileName));
        log.info("End loading excel {}", excelFileName);
    }

    private XlBean generateBlankInstance(String instanceTypeName) {
        XlBean newInstance = XlBeanFactory.getInstance().createBean();
        newInstance.put("instanceType", instanceTypeName);
        return newInstance;
    }

    private boolean evaluateGenerateCondition(XlBean target, List<XlBean> generatedObjects, XlBean instanceType) {
        setupScriptContext(target, generatedObjects);
        String conditionLogic = instanceType.value("condition");
        return (Boolean) shell.evaluate(conditionLogic);
    }

    private void runGeneratorAndPopulateTarget(XlBean target, List<XlBean> additionalBeans) {
        setupScriptContext(target, additionalBeans);

        xlbean.list("generators").forEach(generator -> executeGenerator(generator, target));

    }

    private void setupScriptContext(XlBean targetObject, List<XlBean> additionalBeans) {
        xlbean.forEach((key, value) -> shell.setProperty(key, value));
        shell.setProperty("xlbean", xlbean);
        shell.setProperty("_this", targetObject);
        additionalBeans.forEach(bean -> {
            shell.setProperty(String.format("_%s", bean.value("instanceType")), bean);
        });
    }

    private void executeGenerator(XlBean generator, XlBean target) {
        String instanceType = target.value("instanceType");
        String targetField = generator.value("target");
        XlBean logicInstance = generator.bean("logic");
        String generatorLogic = logicInstance.value(instanceType);
        log.trace("{}\t{}", targetField, generatorLogic);

        if (generatorLogic == null) {
            return;
        }

        Object result = shell.evaluate(generatorLogic);
        log.trace("RESULT: " + result);
        if (generator.value("target") != null) {
            log.trace("SET: {} <- {}", generator.value("target"), result);
            FieldAccessHelper.setValue(generator.value("target"), result, target);
        }

    }

}
