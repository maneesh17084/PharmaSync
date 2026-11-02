package com.pharmasync;

import com.pharmasync.db.DatabaseManager;
import com.pharmasync.db.SchemaInitializer;
import com.pharmasync.ui.Card;
import com.pharmasync.ui.Icons;
import com.pharmasync.ui.LoginFrame;
import com.pharmasync.ui.Theme;
import com.pharmasync.ui.UiUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.concurrent.ExecutionException;

/** Entry point: open (or create) the SQLite database file, prepare the schema, then show login. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Theme.setup();
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        boolean demo = false;
        if (!DatabaseManager.databaseExists()) {
            int choice = JOptionPane.showConfirmDialog(null,
                    "A new database will be created at\n" + DatabaseManager.config().getFile()
                            + "\n\nLoad demo data (medicines, stock, purchases and sales)?",
                    "PharmaSync - First start", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION && choice != JOptionPane.NO_OPTION) {
                System.exit(0);
                return;
            }
            demo = choice == JOptionPane.YES_OPTION;
        }
        prepareDatabase(demo);
    }

    private static void prepareDatabase(boolean demo) {
        JWindow progress = splash();
        progress.setVisible(true);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return SchemaInitializer.initialize(demo);
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    boolean created = get();
                    if (created) {
                        UiUtil.info(null, "Database was created" + (demo ? " with demo data" : "")
                                + ".\n\nSign in with admin / admin123 "
                                + "(administrator) or staff / staff123 (staff).\nYou will be asked to choose a new password.");
                    }
                    new LoginFrame().setVisible(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    cause.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Could not prepare the database "
                                    + DatabaseManager.config().getFile() + ":\n" + cause.getMessage(),
                            "PharmaSync", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            }
        }.execute();
    }

    /** Small branded window shown while the schema is created or upgraded. */
    private static JWindow splash() {
        JWindow w = new JWindow();
        w.setIconImages(Icons.windowIcons());
        w.setBackground(new Color(0, 0, 0, 0));
        Card card = new Card(new BorderLayout(16, 0), 18);
        card.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        JLabel name = new JLabel("PharmaSync");
        name.setFont(Theme.font(Font.BOLD, 8));
        JLabel status = UiUtil.muted("Preparing database...");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel text = new JPanel(new BorderLayout(0, 8));
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(status, BorderLayout.CENTER);
        text.add(bar, BorderLayout.SOUTH);
        card.add(new JLabel(Icons.logo(60)), BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        w.setContentPane(card);
        w.setSize(380, 120);
        w.setLocationRelativeTo(null);
        return w;
    }

}
