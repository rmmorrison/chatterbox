package ca.ryanmorrison.chatterbox.extension;

public class Page<T> {

    private T object;
    private int index;
    private int count;

    public Page() {
    }

    public T getObject() {
        return object;
    }

    public int getIndex() {
        return index;
    }

    public int getCount() {
        return count;
    }

    public static class Builder<T> {
        private T object;
        private int index;
        private int count;

        public Builder<T> setObject(T object) {
            this.object = object;
            return this;
        }

        public Builder<T> setIndex(int index) {
            this.index = index;
            return this;
        }

        public Builder<T> setCount(int count) {
            this.count = count;
            return this;
        }

        public Page<T> build() {
            Page<T> page = new Page<>();
            page.object = this.object;
            page.index = this.index;
            page.count = this.count;
            return page;
        }
    }
}
