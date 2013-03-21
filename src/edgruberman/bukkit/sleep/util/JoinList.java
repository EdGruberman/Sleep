package edgruberman.bukkit.sleep.util;

import java.text.FieldPosition;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;

import org.bukkit.configuration.ConfigurationSection;

/**
 * @author EdGruberman (ed@rjump.com)
 * @version 1.2.0
 */
public class JoinList<T> extends ArrayList<T> {

    private static final String CONFIG_KEY_FORMAT = "format";
    private static final String CONFIG_KEY_ITEM = "item";
    private static final String CONFIG_KEY_DELIMITER = "delimiter";

    private static final String DEFAULT_FORMAT = "{0}";
    private static final String DEFAULT_ITEM = "{0}";
    private static final String DEFAULT_DELIMITER = " ";

    private static final long serialVersionUID = 1L;

    private final String format;
    private final String item;
    private final String delimiter;

    public JoinList() {
        this(JoinList.DEFAULT_FORMAT, JoinList.DEFAULT_ITEM, JoinList.DEFAULT_DELIMITER);
    }

    public JoinList(final String format, final String item, final String delimiter) {
        this.format = format;
        this.item = item;
        this.delimiter = delimiter;
    }

    public JoinList(final ConfigurationSection config) {
        this(config.getString(JoinList.CONFIG_KEY_FORMAT, JoinList.DEFAULT_FORMAT)
                , config.getString(JoinList.CONFIG_KEY_ITEM, JoinList.DEFAULT_ITEM)
                , config.getString(JoinList.CONFIG_KEY_DELIMITER, JoinList.DEFAULT_DELIMITER));
    }

    public boolean add(final Object... arguments) {
        return this.add((Object) arguments);
    }

    @Override
    public String toString() {
        final Iterator<T> i = this.iterator();
        if (!i.hasNext()) return MessageFormat.format(this.format, "");

        final StringBuilder items = new StringBuilder();
        while (i.hasNext()) {
            final Object o = i.next();

            // prevent recursion
            if (o == this) {
                items.append("{this}");
                continue;
            }

            // format item, which could either be an array of objects or a single object
            final MessageFormat message = new MessageFormat(this.item);
            final Object[] arguments = ( o instanceof Object[] ? (Object[]) o : new Object[] { o } );
            final StringBuffer sb = message.format(arguments, new StringBuffer(), new FieldPosition(0));
            items.append(sb);

            if (i.hasNext()) items.append(this.delimiter);
        }

        return MessageFormat.format(this.format, items);
    }

}
