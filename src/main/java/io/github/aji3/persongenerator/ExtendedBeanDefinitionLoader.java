package io.github.aji3.persongenerator;

import java.util.List;
import java.util.stream.Collectors;

import org.xlbean.definition.BeanDefinitionLoader;
import org.xlbean.definition.DefinitionRepository;
import org.xlbean.definition.SingleDefinition;
import org.xlbean.definition.TableDefinition;

public class ExtendedBeanDefinitionLoader extends BeanDefinitionLoader {

    private List<String> fieldOrder;
    private String name;

    public ExtendedBeanDefinitionLoader(int i, List<String> order, String name) {
        super(i);
        this.fieldOrder = order;
        this.name = name;
    }

    @Override
    public DefinitionRepository load() {
        DefinitionRepository repository = super.load();

        TableDefinition tableDefinition = (TableDefinition) repository
            .getDefinitions()
            .stream()
            .filter(def -> "persons".equals(def.getName()))
            .findFirst()
            .orElse(null);

        List<SingleDefinition> list = tableDefinition
            .getAttributes()
            .values()
            .stream()
            .sorted((attr1, attr2) ->
            {
                System.out.println(attr2.getName() + "\t" + fieldOrder.indexOf(attr2.getName()));
                return fieldOrder.indexOf(attr2.getName()) - fieldOrder.indexOf(attr1.getName());
            })
            .collect(Collectors.toList());

        return repository;
    }

    @Override
    protected List<SingleDefinition> getAttributesList(TableDefinition tableDefinition) {
        if (name.equals(tableDefinition.getName())) {
            return tableDefinition
                .getAttributes()
                .values()
                .stream()
                .sorted((attr1, attr2) ->
                {
                    System.out.println(attr2.getName() + "\t" + fieldOrder.indexOf(attr2.getName()));
                    int order1 = fieldOrder.indexOf(attr1.getName());
                    int order2 = fieldOrder.indexOf(attr2.getName());
                    if (order1 == -1 && order2 == -1) {
                        return attr1.getName().compareTo(attr2.getName());
                    } else if (order1 == -1) {
                        return 1;
                    } else if (order2 == -1) {
                        return -1;
                    } else {
                        return order1 - order2;
                    }
                })
                .collect(Collectors.toList());
        } else {
            return super.getAttributesList(tableDefinition);
        }
    }

}
