package ca.ryanmorrison.chatterbox.module;

import net.dv8tion.jda.api.JDA;

/**
 * Resources available to a module after JDA is fully ready. Passed to
 * {@link Module#onStart(ModuleContext)}. Extends {@link InitContext} with the
 * live {@link JDA} reference.
 */
public interface ModuleContext extends InitContext {
    JDA jda();
}
