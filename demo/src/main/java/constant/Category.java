package constant;

public enum Category {
    ALL("all");

    private String keyword;

    Category(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
