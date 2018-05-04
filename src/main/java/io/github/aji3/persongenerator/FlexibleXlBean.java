package io.github.aji3.persongenerator;

import org.xlbean.XlBean;

@SuppressWarnings("serial")
public class FlexibleXlBean extends XlBean {

    @Override
    protected boolean canPut(Object value) {
        return true;
    }

}
