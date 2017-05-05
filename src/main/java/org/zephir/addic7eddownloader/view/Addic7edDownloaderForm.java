package org.zephir.addic7eddownloader.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.zephir.addic7eddownloader.core.Addic7edDownloaderConstants;
import org.zephir.addic7eddownloader.core.Addic7edDownloaderCore;
import org.zephir.util.ConsoleFormAppender;

public class Addic7edDownloaderForm implements Addic7edDownloaderConstants {
    private static Logger log = null;
    private Shell sShell = null; // @jve:decl-index=0:visual-constraint="10,10"
    private Label labelInputFolder = null;
    private Text textInputFolder = null;
    private Button buttonInputFolder = null;
    private Button buttonProceed = null;

    private Button checkboxFrenchLang = null;
    private Button checkboxEnglishLang = null;

    public static void main(final String[] args) {
        final Display display = (Display) SWTLoader.getDisplay();
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                log = Logger.getLogger(Addic7edDownloaderForm.class);

                Addic7edDownloaderForm thisClass = new Addic7edDownloaderForm();
                thisClass.createSShell();
                thisClass.sShell.open();

                while (!thisClass.sShell.isDisposed()) {
                    if (!display.readAndDispatch()) {
                        display.sleep();
                    }
                }
                display.dispose();
            }
        });
    }

	private void loadPreferences() {
		try {
			if (new File(PROPERTIES_FILE).exists()) {
				Properties props = new Properties();
				FileInputStream in = new FileInputStream(PROPERTIES_FILE);
				props.load(in);
				in.close();

				String inputFolder = props.getProperty("inputFolder");
				if (inputFolder != null) {
					textInputFolder.setText(inputFolder);
				}
			}
		} catch (IOException e) {
			log.error("loadPreferences() KO: " + e, e);
		}
	}

	private void savePreferences() {
		try {
			Properties props = new Properties();
			props.setProperty("inputFolder", textInputFolder.getText());
			FileOutputStream out = new FileOutputStream(PROPERTIES_FILE);
			props.store(out, "---No Comment---");
			out.close();

		} catch (IOException e) {
			log.error("savePreferences() KO: " + e, e);
		}
	}

    private void createSShell() {
        sShell = new Shell((Display) SWTLoader.getDisplay(), SWT.CLOSE | SWT.TITLE | SWT.MIN | SWT.MAX);
        sShell.setText(APPLICATION_NAME + " by wInd v" + VERSION);
        sShell.setImage(new Image(Display.getCurrent(), getClass().getResourceAsStream("/skull2-16x16.gif")));
        sShell.setLayout(null);
        sShell.addShellListener(new org.eclipse.swt.events.ShellAdapter() {
            @Override
            public void shellClosed(final org.eclipse.swt.events.ShellEvent e) {
				savePreferences();
                ConsoleFormAppender.closeAll();
            }
        });

        int y = FORM_LINE_SPACE;
        labelInputFolder = new Label(sShell, SWT.HORIZONTAL);
        labelInputFolder.setText("Input folder :");
        labelInputFolder.setBounds(new Rectangle(3, y + FORM_LABEL_DELTA, FORM_LINE_TAB, FORM_LINE_HEIGHT));
        textInputFolder = new Text(sShell, SWT.BORDER);
        textInputFolder.setText(USER_DIR);
        textInputFolder.setBounds(new Rectangle(FORM_LINE_TAB + FORM_LINE_SPACE, y, 450, FORM_LINE_HEIGHT));
        textInputFolder.setTextLimit(655);
        buttonInputFolder = new Button(sShell, SWT.NONE);
        buttonInputFolder.setText("...");
        buttonInputFolder.setBounds(new Rectangle(560, y, 29, FORM_LINE_HEIGHT));
        buttonInputFolder.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(final Event event) {
                DirectoryDialog directoryDialog = new DirectoryDialog(sShell);
                directoryDialog.setFilterPath(textInputFolder.getText());
                String dir = directoryDialog.open();
                if (dir != null) {
                    textInputFolder.setText(dir);
                }
            }
        });

        y += FORM_LINE_HEIGHT + FORM_LINE_SPACE;
        checkboxEnglishLang = new Button(sShell, SWT.CHECK);
        checkboxEnglishLang.setText("English");
        checkboxEnglishLang.setSelection(true);
        checkboxEnglishLang.setBounds(new Rectangle(3, y, 100, FORM_LINE_HEIGHT));

        checkboxFrenchLang = new Button(sShell, SWT.CHECK);
        checkboxFrenchLang.setText("French");
        checkboxFrenchLang.setSelection(false);
        checkboxFrenchLang.setBounds(new Rectangle(checkboxEnglishLang.getBounds().x + checkboxEnglishLang.getBounds().width + FORM_LINE_SPACE / 2, y, 100, FORM_LINE_HEIGHT));

        buttonProceed = new Button(sShell, SWT.NONE);
        buttonProceed.setText("Proceed !");
        buttonProceed.setBounds(new Rectangle(386, y, 119, FORM_BUTTON_HEIGHT));
        buttonProceed.addMouseListener(new org.eclipse.swt.events.MouseAdapter() {
            @Override
            public void mouseDown(final org.eclipse.swt.events.MouseEvent e) {
                proceed();
            }
        });

        y += (FORM_LINE_HEIGHT + FORM_LINE_SPACE) * 2;
        sShell.setSize(new Point(600, 100));
		loadPreferences();
    }

//	private void openHelpDialog(final String title, final String text) {
//		MessageBox mb = new MessageBox(sShell, SWT.OK | SWT.ICON_QUESTION);
//		mb.setText(title);
//		mb.setMessage(text);
//		mb.open();
//	}

    private void proceed() {
        try {
            buttonProceed.setEnabled(false);

            final Addic7edDownloaderCore core = new Addic7edDownloaderCore();
            core.setFolderToProcess(new File(textInputFolder.getText()));
            List<Locale> langList = new ArrayList<>();
            if (checkboxFrenchLang.getSelection()) {
                langList.add(Locale.FRENCH);
            }
            if (checkboxEnglishLang.getSelection()) {
                langList.add(Locale.ENGLISH);
            }
            core.setLangList(langList);

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        // show console
                        ConsoleFormAppender.focus();

                        // process
                        core.processFolder();

                    } catch (Exception e) {
                        e.printStackTrace();
                        log.debug("Exception: " + e.toString());

                    } finally {
                        // processing finished
                        Display.getDefault().syncExec(new Runnable() {
                            @Override
                            public void run() {
                                buttonProceed.setEnabled(true);
                            }
                        });
                    }
                }
            };
            new Thread(runnable).start();
        } catch (Exception e) {
            log.error("Error: " + e, e);
            buttonProceed.setEnabled(true);
            return;
        }
    }
}
