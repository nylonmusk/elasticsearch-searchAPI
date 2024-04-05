package constant;

public enum SpecificSearch {
    INCLUDE("+"),
    EXCLUDE("-"),
    EQUAL("\"");
    private String keyword;

    SpecificSearch(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
