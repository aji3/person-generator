package io.github.aji3.persongenerator;

import org.xlbean.XlBean;
import org.xlbean.util.XlBeanFactory;

public class FlexibleXlBeanFactory extends XlBeanFactory {

    @Override
    public XlBean createBean() {
        return new FlexibleXlBean();
    }
}
