package com.example.searchAPI.validator;

import java.util.List;

public class GenericValidator {

    public static boolean isNullOrEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String) {
            return ((String) obj).isEmpty();
        }
        if (obj instanceof List<?>) {
            return ((List<?>) obj).isEmpty();
        }
        if (obj instanceof Integer) {
            return false;
        }
        return false;
    }

    public static boolean isString(Object obj) {
        return obj instanceof String;
    }

    public static boolean isInteger(Object obj) {
        return obj instanceof Integer;
    }

    public static boolean isListOfStrings(Object obj) {
        return obj instanceof List<?> && ((List<?>) obj).stream().allMatch(o -> o instanceof String);
    }
}
