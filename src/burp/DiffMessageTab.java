package burp;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRowGenerator;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DiffMessageTab implements IMessageEditorTab {
    private final JPanel diffyContainer = new JPanel(new BorderLayout());
    private RSyntaxTextArea textEditor = new RSyntaxTextArea(20, 60);
    private RTextScrollPane scrollPane = new RTextScrollPane(textEditor);

    private String red = "#dc3545";
    private String green = "#28a745";
    private String blue = "#0d6efd";

    private Highlighter.HighlightPainter insertPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.decode(green));
    private Highlighter.HighlightPainter modifiedPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.decode(blue));
    private Highlighter.HighlightPainter deletePainter = new DefaultHighlighter.DefaultHighlightPainter(Color.decode(red));
    private byte[] currentMessage;
    private byte[] lastMessage;
    private int lastPort;
    private String lastHost;
    private String lastProtocol;
    private Boolean componentShown = false;
    private final int MAX_BYTES = 750000;
    private IMessageEditorController controller;

    public DiffMessageTab(IMessageEditorController controller) {
        this.controller = controller;
        diffyContainer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if(componentShown) {
                    return;
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        diffyContainer.removeAll();
                        textEditor.setLineWrap(true);
                        textEditor.setEditable(false);
                        textEditor.setAntiAliasingEnabled(false);
                        scrollPane.setAutoscrolls(true);
                        DefaultCaret caret = (DefaultCaret) textEditor.getCaret();
                        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
                        try {
                            Theme theme = Theme.load(getClass().getResourceAsStream(
                                    "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                            theme.apply(textEditor);
                        } catch (IOException ioe) {
                        }
                        diffyContainer.add(scrollPane);
                    }
                });
                componentShown = true;
            }
        });
    }

    @Override
    public String getTabCaption() {
        return "Diff";
    }

    @Override
    public Component getUiComponent() {
        return diffyContainer;
    }

    @Override
    public boolean isEnabled(byte[] content, boolean isRequest) {
        return !isRequest;
    }

    @Override
    public void setMessage(byte[] content, boolean isRequest) {
        if(isRequest) {
           return;
        }
        if (content != null && content.length > 0) {

            int currentPort = controller.getHttpService().getPort();
            String currentHost = controller.getHttpService().getHost();
            String currentProtocol = controller.getHttpService().getProtocol();

            if(currentMessage != content) {
                if(content.length > MAX_BYTES) {
                    textEditor.setText("Response is too large to diff");
                    return;
                }

                textEditor.setText(Utilities.helpers.bytesToString(content));
                textEditor.removeAllLineHighlights();
                if(isLastService(currentPort, currentHost, currentProtocol) && lastMessage != null && lastMessage != content && lastMessage.length > 0) {
                    java.util.List<String> currentResponse = Arrays.asList(Utilities.helpers.bytesToString(content).split("\\n"));
                    java.util.List<String> previousResponse  = Arrays.asList(Utilities.helpers.bytesToString(lastMessage).split("\\n"));
                    Highlighter highlighter = textEditor.getHighlighter();

                    Patch<String> patch = DiffUtils.diff(previousResponse, currentResponse);
                    List<AbstractDelta<String>> deltas = patch.getDeltas();
                    for (AbstractDelta<String> delta : deltas) {
                        switch (delta.getType()) {
                            case INSERT:
                                try {
                                    textEditor.addLineHighlight(delta.getTarget().getPosition(), Color.decode(green));
                                } catch (BadLocationException e) {

                                }
                                break;
                            case CHANGE:
                                int linePos = delta.getTarget().getPosition();
                                int pos = 0;
                                for (int i = 0; i < linePos; i++) {
                                    pos += currentResponse.get(i).length() + 1;
                                }
                                int finalPos = pos;
                                DiffRowGenerator generator = DiffRowGenerator.create()
                                        .showInlineDiffs(true)
                                        .mergeOriginalRevised(true)
                                        .inlineDiffByWord(true)
                                        .lineNormalizer(f -> f)
                                        .processDiffs(diff-> {
                                            String line = currentResponse.get(linePos);
                                            int foundPos = line.indexOf(diff);
                                            if(foundPos != -1) {
                                                int start = finalPos + foundPos;
                                                int end = start + diff.length();
                                                addHighlight(start, end, highlighter, modifiedPainter);
                                            }
                                            return diff;
                                        })
                                        .build();

                                generator.generateDiffRows(
                                        delta.getSource().getLines(),
                                        delta.getTarget().getLines());
                                break;
                        }
                    }
                }
            }
            lastMessage = currentMessage;
            lastPort = controller.getHttpService().getPort();
            lastHost = controller.getHttpService().getHost();
            lastProtocol = controller.getHttpService().getProtocol();
        }
        currentMessage = content;
    }
    @Override
    public byte[] getMessage() {
        return currentMessage;
    }

    private void addHighlight(int startPos, int endPos, Highlighter highlighter, Highlighter.HighlightPainter painter) {
        try {
            highlighter.addHighlight(startPos, endPos, painter);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isModified() {
        return false;
    }

    public boolean isLastService(int currentPort, String currentHost, String currentProtocol) {
        if(lastPort == 0 || lastHost == null || lastProtocol == null) {
            return true;
        }
        return currentPort == lastPort && currentHost.equals(lastHost) && currentProtocol.equals(lastProtocol);
    }

    @Override
    public byte[] getSelectedData() {
        return null;
    }
}