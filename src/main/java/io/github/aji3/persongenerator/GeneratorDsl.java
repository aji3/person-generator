package io.github.aji3.persongenerator;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ibm.icu.text.Transliterator;

import groovy.lang.Script;

public abstract class GeneratorDsl extends Script {

    public String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public Object randomFrom(List<?> list) {
        int index = (int) (Math.random() * list.size());
        return list.get(index);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> typelist(String type) {
        List<Map<String, Object>> typelists = (List<Map<String, Object>>) getProperty("typelists");
        return (List<Map<String, Object>>) typelists
            .stream()
            .filter(bean -> type.equals(bean.get("type")))
            .collect(
                Collectors.toList());
    }

    public Map<String, Object> typelistAsMap(String type) {
        final Map<String, Object> ret = new HashMap<>();
        typelist(type).forEach(bean -> ret.put(bean.get("key").toString(), bean.get("value")));
        return ret;
    }

    public String typelistValue(String type, String key) {
        return typelist(type)
            .stream()
            .filter(elem -> key.equals(elem.get("key")))
            .findFirst()
            .map(Object::toString)
            .orElse(null);
    }

    public Object randomFromTypelist(String type) {
        return randomFrom(typelist(type));
    }

    public Object randomFromTypelistNot(String type, String excludeKey) {
        List<Map<String, Object>> excludedList = typelist(type)
            .stream()
            .filter(elem -> !excludeKey.equals(elem.get("key")))
            .collect(Collectors.toList());
        return randomFrom(excludedList);
    }

    public int randomIntBetween(int from, int to) {
        if (from > to) {
            return from;
        }
        return (int) ((to - from) * Math.random() + from);
    }

    public String generateDateOfBirthBetween(int ageFrom, int ageTo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDate dateOfBirth = LocalDate.now().minus(Period.ofDays((ageTo - ageFrom) * 365));

        return formatter.format(dateOfBirth);
    }

    public String generateDateOfBirth(int age) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDate dateOfBirth = LocalDate.now().minus(Period.ofDays(age * 365));

        return formatter.format(dateOfBirth);
    }

    @SuppressWarnings("rawtypes")
    public String generateEmailFrom(String... strings) {
        String email = String.join("_", strings) + "@" + ((Map) randomFrom((List) getProperty("emailDomains"))).get(
            "value");
        Transliterator transliterator = Transliterator.getInstance("Katakana-Latin");
        return transliterator.transliterate(email);
    }

    public String generatePhone(String phoneType) {
        switch (phoneType) {
        case "HOME":
            return String.format("03-%04d-%04d", (int) (Math.random() * 10000), (int) (Math.random() * 10000));
        case "MOBILE":
            return String.format("090-%04d-%04d", (int) (Math.random() * 10000), (int) (Math.random() * 10000));
        }
        return "";
    }

    public Boolean randomBoolean() {
        return randomBoolean(0.5);
    }

    public Boolean randomBoolean(double trueRatio) {
        return Math.random() <= trueRatio;
    }

    public String randomDigit(int num) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < num; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }
}
