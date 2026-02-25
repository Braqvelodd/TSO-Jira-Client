// File: src/tso/usmc/jira/util/ConfigChangeListener.java
package tso.usmc.jira.util;

import java.util.EventListener;

/**
 * A listener interface for receiving configuration change events.
 */
public interface ConfigChangeListener extends EventListener {
    /**
     * Invoked when the configuration has been reloaded.
     */
    void onConfigChanged();
}
