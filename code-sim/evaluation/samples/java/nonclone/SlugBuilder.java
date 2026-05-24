public class SlugBuilder {
    public String slug(String title) {
        return title.trim().toLowerCase().replace(" ", "-");
    }
}
