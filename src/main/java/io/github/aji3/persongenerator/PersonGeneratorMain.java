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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import groovy.lang.Script;

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

        String targetType = xlbean.value("targetType");

        for (int i = 0; i < numberToGenerate; i++) {
            log.info("Start {}", i);

            resultList.addAll(generate(targetType));

            log.info("End {}", i);
        }

        log.info("Start output");

        writeToExcel(resultList, now);
        writeToJson(resultList, now);
        log.info("End output");
    }

    private List<XlBean> generate(String targetType) {
        List<XlBean> resultList = new ArrayList<>();
        for (XlBean instanceType : xlbean.list("instanceTypes")) {
            XlBean target = generateBlankInstance(targetType, instanceType);
            if (evaluateGenerateCondition(resultList, instanceType)) {
                log.info("Start generate {} {}", targetType, instanceType.value("name"));
                runGeneratorAndPopulateTarget(getGenerators(targetType), target, resultList);
                resultList.add(target);
                log.info("End generate {} {}", targetType, instanceType.value("name"));
            } else {
                log.info("Skipped {} {}", targetType, instanceType.value("name"));
            }
        }
        return resultList;
    }

    private List<XlBean> getGenerators(String type) {
        return xlbean
            .list("generators")
            .stream()
            .filter(item -> "PERSON".equals(item.bean("target").value("type")))
            .collect(Collectors.toList());
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

    private XlBean generateBlankInstance(String type, XlBean instanceType) {
        XlBean newInstance = XlBeanFactory.getInstance().createBean();
        newInstance.put("_instanceType", type);
        newInstance.put("_instanceName", instanceType.value("name"));
        return newInstance;
    }

    private boolean evaluateGenerateCondition(List<XlBean> generatedObjects, XlBean instanceType) {
        setupScriptContext(null, generatedObjects);
        String conditionLogic = instanceType.value("condition");
        return (Boolean) shell.evaluate(conditionLogic);
    }

    private void runGeneratorAndPopulateTarget(List<XlBean> generators, XlBean target, List<XlBean> additionalBeans) {
        setupScriptContext(target, additionalBeans);

        generators.forEach(generator -> executeGenerator(generator, target));

    }

    private void setupScriptContext(XlBean targetObject, List<XlBean> additionalBeans) {
        xlbean.forEach((key, value) -> shell.setProperty(key, value));
        shell.setProperty("xlbean", xlbean);
        shell.setProperty("_this", targetObject);
        additionalBeans.forEach(bean -> {
            shell.setProperty(String.format("_%s", bean.value("_instanceName")), bean);
        });
    }

    private Map<String, Script> scriptMap = new HashMap<>();

    private Script getScript(String logic) {
        Script ret = scriptMap.get(logic);
        if (ret == null) {
            ret = shell.parse(logic);
            scriptMap.put(logic, ret);
        }
        return ret;
    }

    private void executeGenerator(XlBean generator, XlBean target) {
        String instanceName = target.value("_instanceName");
        String targetField = generator.bean("target").value("field");
        XlBean logicInstance = generator.bean("logic");
        String generatorLogic = logicInstance.value(instanceName);
        log.trace("{}\t{}", targetField, generatorLogic);

        if (generatorLogic == null) {
            return;
        }

        Object result = getScript(generatorLogic).run();
        log.trace("RESULT: " + result);
        if (targetField != null) {
            log.trace("SET: {} <- {}", targetField, result);
            FieldAccessHelper.setValue(targetField, result, target);
        }

    }

}
