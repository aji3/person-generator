package io.github.aji3.persongenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xlbean.XlBean;
import org.xlbean.util.FieldAccessHelper;
import org.xlbean.util.XlBeanFactory;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public class PersonGenerator {

    private static Logger log = LoggerFactory.getLogger(PersonGenerator.class);

    private GroovyShell shell;
    private XlBean xlbean;

    public PersonGenerator(XlBean xlbean) {

        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass("io.github.aji3.persongenerator.GeneratorDsl");
        Binding binding = new Binding();
        binding.setProperty("xlbean", xlbean);
        shell = new GroovyShell(binding, config);

        this.xlbean = xlbean;
    }

    public List<XlBean> generate(String targetType) {
        List<XlBean> resultList = new ArrayList<>();
        for (XlBean instanceType : xlbean
            .list("instanceTypes")
            .stream()
            .filter(type -> targetType.equals(type.value("type")))
            .collect(Collectors.toList())) {
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

    private List<XlBean> getGenerators(String targetType) {
        return xlbean
            .list("generators")
            .stream()
            .filter(item -> targetType.equals(item.bean("target").value("type")))
            .collect(Collectors.toList());
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
