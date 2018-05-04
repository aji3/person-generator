package io.github.aji3.persongenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xlbean.XlBean;
import org.xlbean.reader.XlBeanReader;
import org.xlbean.util.FieldAccessHelper;
import org.xlbean.util.XlBeanFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import groovy.lang.GroovyShell;

public class PersonGeneratorMain {

    private static Logger log = LoggerFactory.getLogger(PersonGeneratorMain.class);

    public static void main(String[] args) {
        new PersonGeneratorMain().run();
    }

    private GroovyShell shell;

    public void run() {

        init();

        XlBean xlbean = new XlBeanReader().read(new File("person_generator.xlsx"));
        System.out.println(xlbean);

        List<XlBean> list = new ArrayList<>();

        int numberToGenerate = 10;
        try {
            numberToGenerate = Integer.parseInt(xlbean.value("numberToGenerate"));
        } catch (NumberFormatException e) {
            System.err.println("Illegal number format for numberToGenerate field. Default number 10 is used.");
        }
        log.info("Start generating {} data.", numberToGenerate);

        for (int i = 0; i < numberToGenerate; i++) {
            log.info("Start {}", i);
            XlBean person = generate(xlbean);
            list.add(person);
            if ((Boolean) FieldAccessHelper.getValue("family.married", person)) {
                log.info("Start {} spouse", i);
                XlBean spouse = generateSpouse(xlbean, person);
                log.info("End {} spouse", i);
                list.add(spouse);
            }
            if ((Boolean) FieldAccessHelper.getValue("family.hasChild", person)) {
                log.info("Start {} child", i);
                XlBean child = generateChild(xlbean, person);
                log.info("End {} child", i);
                list.add(child);
            }
            log.info("End {}", i);
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            log.info(mapper.writeValueAsString(list));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void init() {
        XlBeanFactory.setInstance(new FlexibleXlBeanFactory());

        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass("io.github.aji3.persongenerator.GeneratorDsl");
        shell = new GroovyShell(config);
    }

    private XlBean generateSpouse(XlBean xlbean, XlBean me) {
        XlBean spouse = generateInternal(xlbean, me, LogicType.spouseLogic);
        FieldAccessHelper.setValue("family.spouse", me.value("id"), spouse);
        FieldAccessHelper.setValue("family.spouse", spouse.value("id"), me);
        return spouse;
    }

    private XlBean generateChild(XlBean xlbean, XlBean me) {
        XlBean child = generateInternal(xlbean, me, LogicType.childLogic);
        if ("F".equals(me.value("sex"))) {
            FieldAccessHelper.setValue("family.mother", me.value("id"), child);
            String father = FieldAccessHelper.getValue("family.spouse", me);
            FieldAccessHelper.setValue("family.father", father, child);
        } else {
            FieldAccessHelper.setValue("family.father", me.value("id"), child);
            String mother = FieldAccessHelper.getValue("family.spouse", me);
            FieldAccessHelper.setValue("family.mother", mother, child);
        }
        FieldAccessHelper.setValue("family.children[0]", child.value("id"), me);
        return child;

    }

    private XlBean generate(XlBean xlbean) {
        return generateInternal(xlbean, null, LogicType.logic);
    }

    private XlBean generateInternal(XlBean xlbean, XlBean me, LogicType logicType) {
        final XlBean targetObject = XlBeanFactory.getInstance().createBean();

        xlbean.entrySet().stream().forEach(entry -> shell.setProperty(entry.getKey(), entry.getValue()));
        shell.setProperty("xlbean", xlbean);
        shell.setProperty("_this", targetObject);
        shell.setProperty("_me", me);

        xlbean.list("generators").forEach(bean -> executeGenerator(bean, targetObject, logicType));

        return targetObject;
    }

    private enum LogicType {
        logic, spouseLogic, childLogic
    };

    private void executeGenerator(XlBean bean, XlBean target, LogicType logicType) {
        log.trace(bean.value("target") + "\t" + bean.value("logic"));

        String logicTypeStr = logicType.toString();

        if (bean.value(logicTypeStr) == null) {
            return;
        }

        Object result = shell.evaluate(bean.value(logicTypeStr));
        log.trace("RESULT: " + result);
        if (bean.value("target") != null) {
            log.trace("SET: {} <- {}", bean.value("target"), result);
            FieldAccessHelper.setValue(bean.value("target"), result, target);
        }

    }

}
