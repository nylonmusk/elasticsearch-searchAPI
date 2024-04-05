package constant;

public enum Field {
    ALL("all"),
    TITLE("title"),
    CONTENT("content"),
    WRITER("writer");

    private String keyword;

    Field(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
