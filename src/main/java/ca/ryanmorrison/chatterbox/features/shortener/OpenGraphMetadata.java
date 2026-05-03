package ca.ryanmorrison.chatterbox.features.shortener;

/**
 * Subset of OpenGraph / Twitter Card / HTML metadata used to render link
 * previews on the redirect page. All fields nullable — scrape failures or
 * sparse pages are tolerated.
 */
record OpenGraphMetadata(String title, String description, String image, String siteName) {

    static final OpenGraphMetadata EMPTY = new OpenGraphMetadata(null, null, null, null);

    boolean isEmpty() {
        return title == null && description == null && image == null && siteName == null;
    }
}
