package edu.asu.commons.foraging.ui;

import edu.asu.commons.event.ClientReadyEvent;
import edu.asu.commons.event.Event;
import edu.asu.commons.event.EventChannel;
import edu.asu.commons.foraging.client.ClientDataModel;
import edu.asu.commons.foraging.client.ForagingClient;
import edu.asu.commons.foraging.conf.RoundConfiguration;
import edu.asu.commons.foraging.event.ClientMovementRequest;
import edu.asu.commons.foraging.event.CollectTokenRequest;
import edu.asu.commons.foraging.event.EndRoundEvent;
import edu.asu.commons.foraging.event.MovementEvent;
import edu.asu.commons.foraging.event.PostRoundSanctionUpdateEvent;
import edu.asu.commons.foraging.event.QuizResponseEvent;
import edu.asu.commons.foraging.event.RealTimeSanctionRequest;
import edu.asu.commons.foraging.event.ResetTokenDistributionRequest;
import edu.asu.commons.foraging.model.ClientData;
import edu.asu.commons.foraging.model.Direction;
import edu.asu.commons.foraging.rules.Strategy;
import edu.asu.commons.net.Identifier;
import edu.asu.commons.ui.HtmlEditorPane;
import edu.asu.commons.ui.UserInterfaceUtils;
import edu.asu.commons.util.Duration;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Primary client-side Swing view for foraging experiment.
 *
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 */
public class GameWindow2D implements GameWindow {
    private final static String INSTRUCTIONS_PANEL_NAME = "instructions screen panel";
    private final static String GAME_PANEL_NAME = "foraging game panel";
    private final static String TRUST_GAME_PANEL_NAME = "trust game panel";
    private final static String CHAT_PANEL_NAME = "standalone chat panel";
    private final static String POST_ROUND_SANCTIONING_PANEL_NAME = "post round sanctioning panel";

    private final static int IN_ROUND_CHAT_PANEL_WIDTH = 400;
    public static final int BORDER_PADDING = 10;

    private String currentCardPanel = INSTRUCTIONS_PANEL_NAME;
    private final StringBuilder instructionsBuilder = new StringBuilder();
    private final ClientDataModel dataModel;
    private EventChannel channel;

    private JPanel mainPanel;
    // instructions components.
    private JScrollPane instructionsScrollPane;
    private HtmlEditorPane instructionsEditorPane;

    private JPanel labelPanel;

    private JPanel surveyIdPanel;

    private ChatPanel chatPanel;

    private JLabel informationLabel;

    private JLabel timeLeftLabel;

    private JPanel gamePanel;

    private ForagingClient client;

    private JPanel subjectPanel;
    private SubjectView subjectView;

    private CardLayout cardLayout;

    private ChatPanel inRoundChatPanel;

    private Timer timer;

    // voting components
    private JPanel votingPanel;
    private VotingForm votingForm;
    private HtmlEditorPane votingInstructionsEditorPane;
    private JScrollPane votingInstructionsScrollPane;

    // SwingWorker for generating robot keypresses
    private SwingWorker robotWorker;

    private boolean singlePlayer;

    private int screenNumber = 0;

    private final static Logger logger = Logger.getLogger(GameWindow2D.class.getName());

    // private EnergyLevel energyLevel;

    public GameWindow2D(ForagingClient client) {
        this.client = client;
        this.dataModel = client.getDataModel();
        // FIXME: set the actual screen size dimensions after this JPanel has been initialized...
        this.channel = client.getEventChannel();
        // feed subject view the available screen size so that
        // it can adjust appropriately when given a board size
        // int width = (int) Math.min(Math.floor(size.getWidth()), Math.floor(size.getHeight() * 0.85));
        this.robotWorker = null;
        initGuiComponents();
    }

    /**
     * Instead of invoking specific update methods we invoke just a single
     * method, update() after we're done changing state.
     */
    public void update(final long roundTimeLeft) {
        SwingUtilities.invokeLater(() -> {
            informationLabel.setText(getInformationLabelText());
            timeLeftLabel.setText(getTimeLeftLabelText(roundTimeLeft));
            // FIXME: subjectView.repaint() causes graphical glitches here
            // only when we transition from 3D -> 2D experiment. Find out why.
            getPanel().repaint();
        });
    }

    /**
     * Access to init() and endRound() must be synchronized.
     */
    public synchronized void init() {
        final RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
        singlePlayer = roundConfiguration.isSinglePlayer();
        SwingUtilities.invokeLater(() -> {
            if (roundConfiguration.isFirstRound()) {
                if (roundConfiguration.getParentConfiguration().shouldAskForSurveyId()) {
                    add(getSurveyIdPanel());
                    showPanel(SurveyIdPanel.NAME);
                } else {
                    setInstructions(roundConfiguration.getWelcomeInstructions());
                }
            }
            // don't display next round time, instead wait for the
            // facilitator signal.
            timeLeftLabel.setText("Waiting for facilitator's signal.");
            informationLabel.setText("Waiting for facilitator's signal.");
            // add the next round instructions to the existing debriefing text set by the previous
            // EndRoundEvent.
        });

    }

    private ActionListener createClientReadyListener(final String confirmationMessage) {
        return event -> {
            int selectedOption = JOptionPane.showConfirmDialog(getPanel(),
                    confirmationMessage,
                    "Continue?", JOptionPane.YES_NO_OPTION);
            switch (selectedOption) {
                case JOptionPane.YES_OPTION:
                    setInstructions(dataModel.getExperimentConfiguration().getWaitingRoomInstructions());
                    showInstructionsPanel();
                    client.transmit(new ClientReadyEvent(client.getId(), confirmationMessage));
                    instructionsEditorPane.setActionListener(null);
                    break;
                default:
                    break;
            }
        };
    }

    private ActionListener createMultiScreenInstructionsListener(final RoundConfiguration configuration) {
        return new ActionListener() {
            private int screenNumber = 0;
            @Override
            public void actionPerformed(ActionEvent e) {
                // currently only allow next / continue
                HtmlEditorPane.FormActionEvent event = (HtmlEditorPane.FormActionEvent) e;
                Properties formData = event.getData();
                String action = formData.getProperty("submit", "Continue");
                switch (action) {
                    case "Continue":
                        screenNumber++;
                        break;
                    case "Previous":
                        screenNumber--;
                        break;
                }
                setInstructions(configuration.getInstructions(screenNumber));
            }
        };
    }

    private ActionListener createQuizListener(final RoundConfiguration configuration) {
        return new ActionListener() {

            private boolean submitted = false;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (e instanceof HtmlEditorPane.FormActionEvent) {
                    HtmlEditorPane.FormActionEvent formEvent = (HtmlEditorPane.FormActionEvent) e;
                    StringBuilder builder = new StringBuilder();
                    if (!submitted) {
                        Properties actualAnswers = formEvent.getData();
                        List<String> incorrectQuestionNumbers = new ArrayList<>();
                        List<String> correctAnswers = new ArrayList<>();

                        // iterate through expected answers
                        Map<String, String> quizAnswers = configuration.getQuizAnswers();
                        for (Map.Entry<String, String> entry : quizAnswers.entrySet()) {
                            String questionNumber = entry.getKey();
                            String expectedAnswer = entry.getValue();
                            String actualAnswer = actualAnswers.getProperty(questionNumber);
                            if (actualAnswer == null) {
                                JOptionPane.showMessageDialog(getPanel(), "Please enter a response for question " + questionNumber.toUpperCase() + ".");
                                return;
                            }
                            if (expectedAnswer.equals(actualAnswer)) {
                                correctAnswers.add(questionNumber);
                            } else {
                                // flag the incorrect response
                                incorrectQuestionNumbers.add(questionNumber);
                            }
                        }
                        submitted = true;
                        client.transmit(new QuizResponseEvent(client.getId(), actualAnswers, incorrectQuestionNumbers));
                        builder.append(configuration.getQuizResults(incorrectQuestionNumbers, actualAnswers));
                    } else {
                        client.transmit(new ClientReadyEvent(client.getId(), "Reviewed quiz responses."));
                        configuration.buildInstructions(builder);
                    }
                    // RoundConfiguration now builds the appropriate quiz results page.
                    setInstructions(builder.toString());
                    instructionsEditorPane.setCaretPosition(0);
                }
            }
        };
    }

    /**
     * Invoked when a subject collected token(s) at the given positions.
     *
     * @param positions
     */
    public void collectTokens(Point... positions) {
        subjectView.collectTokens(positions);
    }

    private void startChatTimer() {
        if (timer == null) {
            final RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
            final Duration duration = Duration.create(roundConfiguration.getChatDuration());
            timer = new Timer(1000, actionEvent -> {
                if (duration.hasExpired()) {
                    timeLeftLabel.setText("Chat is now disabled.");
                    timer.stop();
                    timer = null;
                } else {
                    timeLeftLabel.setText(String.format("Chat will end in %d seconds.", duration.getTimeLeft() / 1000L));
                }
            });
            timer.start();
        }
    }

    private String getInformationLabelText() {
        if (dataModel.getRoundConfiguration().isGroupTokenDisplayEnabled()) {
            StringBuilder builder = new StringBuilder("Tokens collected:");
            // XXX: use this method so that we get the proper ordering of client ids/assigned numbers..
            // Map<Identifier, ClientData> clientDataMap = dataModel.getClientDataMap();
            Point clientPosition = dataModel.getCurrentPosition();

            // FIXME: refactor this ugliness.
            for (Identifier id : dataModel.getAllClientIdentifiers()) {
                // ClientData clientData = clientDataMap.get(id);
                String formatString;
                if (id.equals(dataModel.getId())) {
                    formatString = " [%d (you) : %d] ";
                    builder.append(String.format(formatString, dataModel.getAssignedNumber(id), dataModel.getCurrentTokens(id)));
                } else {
                    if (!dataModel.getRoundConfiguration().isFieldOfVisionEnabled()) {
                        formatString = " [%d : %d] ";
                        builder.append(String.format(formatString, dataModel.getAssignedNumber(id), dataModel.getCurrentTokens(id)));
                    }
                    else {
                        double radius = dataModel.getRoundConfiguration().getViewSubjectsRadius();
                        Circle fieldOfVision = new Circle(clientPosition, radius);
                        if (fieldOfVision.contains(dataModel.getClientPosition(id))) {
                            formatString = " [%d : %d] ";
                            builder.append(String.format(formatString, dataModel.getAssignedNumber(id), dataModel.getCurrentTokens(id)));
                        } else {
                            formatString = " [%d : XX] ";
                            builder.append(String.format(formatString, dataModel.getAssignedNumber(id)));
                        }
                    }
                }
            }
            return builder.toString();
        } else {
            int tokensConsumed = dataModel.getCurrentTokens();
            return String.format("Income: %s  |  Tokens collected: %d     ",
                    NumberFormat.getCurrencyInstance().format(getIncome(tokensConsumed)),
                    tokensConsumed);
        }
    }

    private String getTimeLeftLabelText(long roundTimeLeft) {
        return String.format("Time left: %d second(s)", roundTimeLeft / 1000L);
    }

    private void setInstructions(String s) {
        // System.err.println("Setting instructions to " + s);
        instructionsEditorPane.setText(s);
        instructionsEditorPane.repaint();
        getPanel().repaint();
    }

    private void initGuiComponents() {
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        // default sized subject view
        Dimension subjectViewSize = new Dimension(768, 768);
        subjectView = new SubjectView(subjectViewSize, dataModel);

        // add instructions panel card
        instructionsEditorPane = UserInterfaceUtils.createInstructionsEditorPane(false, 26);
        String liStyle = "li { padding: 8px 0 8px 0;}";
        String btnStyle = ".btn { font-weight: 400; font-size: 1rem; line-height: 1.5; text-align: center; }";
        UserInterfaceUtils.addCss(instructionsEditorPane, liStyle, btnStyle);
        instructionsScrollPane = new JScrollPane(instructionsEditorPane);
        instructionsScrollPane.setDoubleBuffered(true);
        instructionsScrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        instructionsScrollPane.setName(INSTRUCTIONS_PANEL_NAME);
        add(instructionsScrollPane);

        // FIXME: use a more flexible LayoutManager for game panel so in-round chat isn't squeezed all the way on the
        // right side of the screen.
        gamePanel = new JPanel(new BorderLayout(6, 6));
        gamePanel.setBorder(new EmptyBorder(BORDER_PADDING, BORDER_PADDING, BORDER_PADDING, BORDER_PADDING));
        gamePanel.setBackground(UserInterfaceUtils.OFF_WHITE);

        gamePanel.setName(GAME_PANEL_NAME);
        // add labels to game panel
        // FIXME: replace with progress bar.
        timeLeftLabel = new JLabel("Connecting ...");
        timeLeftLabel.setFont(UserInterfaceUtils.DEFAULT_BOLD_FONT);
        informationLabel = new JLabel("Tokens collected: 0     ");
        informationLabel.setFont(UserInterfaceUtils.DEFAULT_BOLD_FONT);
        // latencyLabel = new JLabel("Latency: 0");
        informationLabel.setBackground(UserInterfaceUtils.OFF_WHITE);
        informationLabel.setForeground(UserInterfaceUtils.DARK_BLUE);

        labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.LINE_AXIS));
        labelPanel.setBackground(UserInterfaceUtils.OFF_WHITE);
        labelPanel.add(timeLeftLabel);
        labelPanel.add(Box.createHorizontalGlue());
        labelPanel.add(informationLabel);

        subjectPanel = new JPanel();
        subjectPanel.setLayout(new GridLayout(1, 2));
        subjectPanel.add(subjectView);
        subjectPanel.add(getInRoundChatPanel());
        subjectView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
            }
        });
        gamePanel.add(subjectPanel, BorderLayout.CENTER);
        gamePanel.add(labelPanel, BorderLayout.PAGE_START);

        add(gamePanel);
        setupKeyBindings(mainPanel);
        // resize listener
        mainPanel.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent event) {
                RoundConfiguration configuration = dataModel.getRoundConfiguration();
                if (configuration == null) {
                    return;
                }
                Component component = event.getComponent();
                int width = component.getWidth() / 2;
                Dimension screenSize = new Dimension(width, (int) (component.getHeight() * 0.85d));
                subjectView.setScreenSize(screenSize);
                subjectView.setImageSizes();
                getPanel().revalidate();
                showPanel(currentCardPanel);
            }
        });
        SwingUtilities.invokeLater(() -> requestFocusInWindow());
    }

    private Action createMovementAction(Direction direction) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Event event;
                if (singlePlayer) {
                    dataModel.moveClient(direction);
                    event = new MovementEvent(client.getId(), direction);
                    subjectView.repaint();
                }
                else {
                    event = new ClientMovementRequest(client.getId(), direction);
                }
                channel.handle(event);
            }
        };
    }

    private Action createSanctionAction(Integer key) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!dataModel.isSanctioningAllowed()) {
                    // FIXME: get rid of magic constants
                    displayErrorMessage("You may not reduce other participants tokens at this time.");
                    return;
                }
                // if (client.canPerformRealTimeSanction()) {
                // Perform the same check as above, except don't check number of available tokens
                // - let the server handle that and send an appropriate error message.
                if (dataModel.isMonitor() || dataModel.isSanctioningAllowed()) {
                    int assignedNumber = key.intValue();
                    Identifier sanctionee = dataModel.getClientId(assignedNumber);
                    System.err.println("Sanctioning : " + sanctionee);
                    if (sanctionee == null || sanctionee.equals(dataModel.getId())) {
                        // don't allow self-flagellation :-).
                        return;
                    }
                    // only allow sanctions for subjects within this subject's field of vision
                    Point subjectPosition = dataModel.getClientPosition(sanctionee);
                    if (dataModel.getClientData().isSubjectInFieldOfVision(subjectPosition)) {
                        channel.handle(new RealTimeSanctionRequest(dataModel.getId(), sanctionee));
                    } else {
                        displayErrorMessage("The participant is out of range.");
                        return;
                    }
                }
            }
        };
    }

    private void setupKeyBindings(JPanel panel) {
        for (String upKeyStroke: Arrays.asList("UP", "W", "I")) {
            panel.getInputMap().put(KeyStroke.getKeyStroke(upKeyStroke), "up");
        }
        panel.getActionMap().put("up", createMovementAction(Direction.UP));

        for (String rightKeyStroke: Arrays.asList("RIGHT", "D", "L")) {
            panel.getInputMap().put(KeyStroke.getKeyStroke(rightKeyStroke), "right");
        }
        panel.getActionMap().put("right", createMovementAction(Direction.RIGHT));

        for (String leftKeyStroke: Arrays.asList("LEFT", "A", "J")) {
            panel.getInputMap().put(KeyStroke.getKeyStroke(leftKeyStroke), "left");
        }
        panel.getActionMap().put("left", createMovementAction(Direction.LEFT));

        for (String downKeyStroke: Arrays.asList("DOWN", "S", "K")) {
            panel.getInputMap().put(KeyStroke.getKeyStroke(downKeyStroke), "down");
        }
        panel.getActionMap().put("down", createMovementAction(Direction.DOWN));

        panel.getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "collect");
        panel.getActionMap().put("collect", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (dataModel.isHarvestingAllowed()) {
                        channel.handle(new CollectTokenRequest(client.getId(),
                                singlePlayer ? dataModel.getCurrentPosition() : null));
                    }
                    else {
                        displayErrorMessage("You cannot harvest at this time.");
                    }
                } catch (RuntimeException exception) {
                    displayErrorMessage("You cannot harvest at this time");
                }
            }
        });
        for (Integer numericKey: IntStream.range(0, 10).boxed().collect(Collectors.toList())) {
            String key = String.valueOf(numericKey);
            panel.getInputMap().put(KeyStroke.getKeyStroke(key), key);
            panel.getActionMap().put(key, createSanctionAction(numericKey));
        }

        panel.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "focusChat");
        panel.getActionMap().put("focusChat", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (dataModel.getRoundConfiguration().isInRoundChatEnabled()) {
                    getInRoundChatPanel().setTextFieldFocus();
                }
            }
        });

        panel.getInputMap().put(KeyStroke.getKeyStroke("R"), "resetTokens");
        panel.getActionMap().put("resetTokens", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                logger.info("Resetting token distribution");
                if (canResetTokenDistribution()) {
                    channel.handle(new ResetTokenDistributionRequest(client.getId()));
                }
            }
        });

        panel.getInputMap().put(KeyStroke.getKeyStroke("M"), "toggleExplicitCollection");
        panel.getActionMap().put("toggleExplicitCollection", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                // M toggles explicit collection mode if enabled
                if (!dataModel.getRoundConfiguration().isAlwaysInExplicitCollectionMode()) {
                    dataModel.toggleExplicitCollectionMode();
                }
            }
        });
    }

    /**
     * Primary handler for client keyboard inputs within the game.
     */
    private KeyAdapter createGameWindowKeyListener() {
        return new KeyAdapter() {
            private volatile boolean keyReleased;

            // FIXME: the keyReleased/keyPressed stuff here only seems to work on Windows.
            // Linux keyboards generate pairs of keyPressed/keyReleased events in tandem even
            // when you are keeping the key down the whole time!
            @Override
            public void keyReleased(KeyEvent keyEvent) {
                keyReleased = true;
            }

            // FIXME: refactor this method if possible.
            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (!client.isRoundInProgress()) {
                    // check for reconnect signal
                    if (keyEvent.getKeyChar() == 'c') {
                        if (keyEvent.isShiftDown() && keyEvent.isControlDown()) {
                            client.connect();
                        }
                    }
                    return;
                }
                int keyChar = (int) keyEvent.getKeyChar();
                int keyCode = keyEvent.getKeyCode();
                Event event = null;
                // directions are the most common action, check for them first.
                Direction direction = Direction.valueOf(keyCode);
                if (direction == null) {
                    // check to see if the key is something else.
                    switch (keyCode) {
                        // token request handling
                        case KeyEvent.VK_SPACE:
                            try {
                                if (dataModel.isHarvestingAllowed()) {
                                    event = new CollectTokenRequest(client.getId(), singlePlayer ? dataModel.getCurrentPosition() : null);
                                } else {
                                    displayErrorMessage("You cannot harvest at this time.");
                                }
                            } catch (RuntimeException exception) {
                                displayErrorMessage("You cannot harvest at this time");
                            }
                            break;
                        // real-time sanctioning keycode handling, currently limited to 10 other group members marked by 0-9
                        case KeyEvent.VK_0:
                        case KeyEvent.VK_1:
                        case KeyEvent.VK_2:
                        case KeyEvent.VK_3:
                        case KeyEvent.VK_4:
                        case KeyEvent.VK_5:
                        case KeyEvent.VK_6:
                        case KeyEvent.VK_7:
                        case KeyEvent.VK_8:
                        case KeyEvent.VK_9:
                            if (!dataModel.isSanctioningAllowed()) {
                                // FIXME: get rid of magic constants
                                // displayErrorMessage("You may not reduce other participants tokens at this time.");
                                return;
                            }

                            // if (client.canPerformRealTimeSanction()) {
                            // Perform the same check as above, except don't check number of available tokens
                            // - let the server handle that and send an appropriate error message.
                            if (dataModel.isMonitor() || dataModel.isSanctioningAllowed()) {
                                int assignedNumber = keyChar - 48;
                                Identifier sanctionee = dataModel.getClientId(assignedNumber);
                                System.err.println("Punishing : " + sanctionee);
                                if (sanctionee == null || sanctionee.equals(dataModel.getId())) {
                                    // don't allow self-flagellation :-).
                                    return;
                                }
                                // only allow sanctions for subjects within this subject's field of vision
                                Point subjectPosition = dataModel.getClientPosition(sanctionee);
                                if (dataModel.getClientData().isSubjectInFieldOfVision(subjectPosition)) {
                                    event = new RealTimeSanctionRequest(dataModel.getId(), sanctionee);
                                    System.out.println("sending sanctioning event : " + event);
                                } else {
                                    displayErrorMessage("The participant is out of range.");
                                    return;
                                }
                            }
                            break;
                        case KeyEvent.VK_ENTER:
                            // set focus on in round chat panel on enter key press as a convenience shortcut
                            if (dataModel.getRoundConfiguration().isInRoundChatEnabled()) {
                                getInRoundChatPanel().setTextFieldFocus();
                            }
                            return;
                        case KeyEvent.VK_R:
                            // R resets token distribution in private property practice rounds
                            if (canResetTokenDistribution()) {
                                event = new ResetTokenDistributionRequest(client.getId());
                            } else
                                return;
                            break;
                        case KeyEvent.VK_M:
                            // M toggles explicit collection mode if enabled
                            if (!dataModel.getRoundConfiguration().isAlwaysInExplicitCollectionMode()) {
                                dataModel.toggleExplicitCollectionMode();
                            }
                            return;
                        default:
                            System.err.println("Invalid input:" + KeyEvent.getKeyText(keyCode));
                    }
                }
                // we have a valid direction, check it
                else if (singlePlayer) {
                    dataModel.moveClient(direction);
                    event = new MovementEvent(client.getId(), direction);
                    subjectView.repaint();
//                    SwingUtilities.invokeLater(() -> subjectView.repaint());
                } else {
                    event = new ClientMovementRequest(client.getId(), direction);
                }
                if (keyReleased) {
                    channel.handle(event);
                    keyReleased = false;
                }
            }
        };

    }

    private boolean canResetTokenDistribution() {
        RoundConfiguration configuration = dataModel.getRoundConfiguration();
        return configuration.isPracticeRound() && configuration.isPrivateProperty();
    }

    public synchronized void startRound() {
        final RoundConfiguration configuration = dataModel.getRoundConfiguration();
        screenNumber = 0;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        Runnable runnable = () -> {
            subjectView.setup(configuration);
            // reset the amount of time left in the round on food eaten
            // label to the value from the configuration file.
            // this is NOT dynamic; once the StartRoundEvent is fired off
            // by the server no new clients can connect because the round
            // has begun.
            update(configuration.getRoundDuration().getTimeLeft());
            if (configuration.isInRoundChatEnabled()) {
                ChatPanel chatPanel = getInRoundChatPanel();
                chatPanel.initialize(dataModel);
            }
            showPanel(GAME_PANEL_NAME);

            // Send a resize event to ensure that the subjectView is sized
            // to accommodate the in-round chat panel
            mainPanel.dispatchEvent(new ComponentEvent(mainPanel, ComponentEvent.COMPONENT_RESIZED));
        };
        SwingUtilities.invokeLater(runnable);

        if (configuration.isRobotControlled()) {
            startRobotWorker(configuration);
        }
    }

    /**
     * Start the SwingWorker thread that generates random player input
     */
    public void startRobotWorker(final RoundConfiguration configuration) {

        // java.awt.Robot was my first choice for generating keyboard input;
        // however, it doesn't seem to have permission to run in Java Web Start
        // applications.

        robotWorker = new SwingWorker<Void, Void>() {
            @Override
            public Void doInBackground() {

                KeyListener keyListener = mainPanel.getKeyListeners()[0];

                KeyEvent keyPressedEvent;
                KeyEvent keyReleasedEvent;
                int[] arrowKeyCodes = {
                        KeyEvent.VK_UP,
                        KeyEvent.VK_DOWN,
                        KeyEvent.VK_LEFT,
                        KeyEvent.VK_RIGHT
                };
                int keyCode;
                Random random = new Random();
                int sleepInterval = 1000 / configuration.getRobotMovesPerSecond();
                double harvestProbability = configuration.getRobotHarvestProbability();

                while (!isCancelled()) {

                    // Move in a random direction
                    keyCode = arrowKeyCodes[random.nextInt(4)];
                    keyPressedEvent = new KeyEvent(mainPanel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
                    keyReleasedEvent = new KeyEvent(mainPanel, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
                    keyListener.keyPressed(keyPressedEvent);
                    keyListener.keyReleased(keyReleasedEvent);

                    if (random.nextDouble() < harvestProbability) {
                        // Collect a token
                        keyPressedEvent = new KeyEvent(mainPanel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
                        keyReleasedEvent = new KeyEvent(mainPanel, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
                        keyListener.keyPressed(keyPressedEvent);
                        keyListener.keyReleased(keyReleasedEvent);
                    }

                    try {
                        Thread.sleep(sleepInterval);
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
                return null;
            }
        };
        robotWorker.execute();
    }

    public void displayErrorMessage(String errorMessage) {
        displayMessage(errorMessage, Color.RED);
    }

    public void displayMessage(String message) {
        displayMessage(message, Color.BLACK);
    }

    public void displayMessage(String message, Color color) {
        getInRoundChatPanel().displayMessage(message, color);
    }

    private double getIncome(int numTokens) {
        return dataModel.getRoundConfiguration().tokensToDollars(numTokens);
    }

    public void showDebriefing(ClientData clientData, boolean showExitInstructions) {
        instructionsBuilder.delete(0, instructionsBuilder.length());
        instructionsBuilder.append(dataModel.getRoundConfiguration().generateClientDebriefing(clientData, showExitInstructions));
        setInstructions(instructionsBuilder.toString());
    }

    private ChatPanel getChatPanel() {
        if (chatPanel == null) {
            chatPanel = new ChatPanel(client);
            chatPanel.setName(CHAT_PANEL_NAME);
            add(chatPanel);
        }
        return chatPanel;
    }

    private ChatPanel getInRoundChatPanel() {
        if (inRoundChatPanel == null) {
            inRoundChatPanel = new ChatPanel(client, true);
            inRoundChatPanel.disableChat();
        }
        return inRoundChatPanel;
    }

    public void showTrustGame() {
        final RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
        if (roundConfiguration.isTrustGameEnabled()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    JPanel panel = new JPanel();
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                    JEditorPane trustGameInstructionsEditorPane = UserInterfaceUtils.createInstructionsEditorPane();
                    JScrollPane scrollPane = new JScrollPane(trustGameInstructionsEditorPane);
                    trustGameInstructionsEditorPane.setText(roundConfiguration.getTrustGameInstructions());
                    panel.add(scrollPane);
                    TrustGamePanel trustGamePanel = new TrustGamePanel(client);
                    JScrollPane trustGameScrollPane = new JScrollPane(trustGamePanel);
                    panel.add(trustGameScrollPane);
                    panel.setName(TRUST_GAME_PANEL_NAME);
                    add(panel);
                    showPanel(TRUST_GAME_PANEL_NAME);
                }
            });
        }
    }

    public void trustGameSubmitted() {
        // FIXME: replace HTML strings with configuration template
        instructionsBuilder.append("<h3>Submission successful</h3><hr><p>Please wait while the rest of the submissions are gathered.</p>");
        setInstructions(instructionsBuilder.toString());
        showInstructionsPanel();
    }

    public void showInstructions(boolean summarized) {
        final RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
        instructionsBuilder.delete(0, instructionsBuilder.length());
        if (summarized) {
            roundConfiguration.buildSummarizedInstructions(instructionsBuilder);
        }
        else if (roundConfiguration.isMultiScreenInstructionsEnabled()) {
            // FIXME: use setActionListener to avoid conflict between listeners if we can't disambiguate the
            // generated events properly
            // instructionsEditorPane.setActionListener(createMultiScreenInstructionsListener(roundConfiguration));
            roundConfiguration.buildInstructions(instructionsBuilder, 0);
        }
        else {
            roundConfiguration.buildAllInstructions(instructionsBuilder);
        }
        if (roundConfiguration.isQuizEnabled()) {
            instructionsEditorPane.setActionListener(createQuizListener(roundConfiguration));
        }
        showInstructions();
    }

    public void showInstructions(int screenNumber) {
        final RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
        if (roundConfiguration.isMultiScreenInstructionsEnabled()) {
            instructionsBuilder.delete(0, instructionsBuilder.length());
            roundConfiguration.buildInstructions(instructionsBuilder, screenNumber);
        }
        showInstructions();
    }

    /**
     * Displays the current state of the instructionsBuilder instance on the instructionsEditorPane.
     */
    public void showInstructions() {
        SwingUtilities.invokeLater(() -> {
            setInstructions(instructionsBuilder.toString());
            showInstructionsPanel();
            instructionsEditorPane.setCaretPosition(0);
        });
    }

    public void showInitialVotingInstructions() {
        SwingUtilities.invokeLater(() -> {
            // instructionsEditorPane.setActionListener(null);
            // instructionsEditorPane.setActionListener(createClientReadyListener("Are you ready to submit your nominations?"));
            setInstructions(dataModel.getRoundConfiguration().getInitialVotingInstructions());
            showInstructionsPanel();
        });
    }

    public void showVotingScreen() {
        SwingUtilities.invokeLater(() -> {
            if (votingPanel == null) {
                votingPanel = new JPanel();
                votingPanel.setLayout(new BoxLayout(votingPanel, BoxLayout.Y_AXIS));
                votingInstructionsEditorPane = UserInterfaceUtils.createInstructionsEditorPane();
                votingInstructionsScrollPane = new JScrollPane(votingInstructionsEditorPane);
                RoundConfiguration configuration = client.getCurrentRoundConfiguration();
                votingInstructionsEditorPane.setText(configuration.getVotingInstructions());
                votingPanel.add(votingInstructionsScrollPane);
                votingForm = new VotingForm(client);
                votingPanel.add(votingForm);
                votingPanel.setName(VotingForm.NAME);
                add(votingPanel);
            }
            showPanel(VotingForm.NAME);
        });
    }

    public void showVotingResults(final List<Strategy> selectedRules, final Map<Strategy, Integer> votingResults) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                votingPanel.removeAll();
                votingPanel.add(votingInstructionsScrollPane);
                votingPanel.revalidate();
                RoundConfiguration currentRoundConfiguration = client.getCurrentRoundConfiguration();
                votingInstructionsEditorPane.setText(currentRoundConfiguration.generateVotingResults(selectedRules, votingResults));
                showPanel(VotingForm.NAME);
            }
        });
    }

    public void showSurveyInstructions() {
        SwingUtilities.invokeLater(() -> {
            instructionsEditorPane.setActionListener(createClientReadyListener(dataModel.getRoundConfiguration().getSurveyConfirmationMessage()));
            setInstructions(dataModel.getRoundConfiguration().getSurveyInstructions(dataModel.getId()));
            showInstructionsPanel();
        });
    }

    public void showInstructionsPanel() {
        showPanel(INSTRUCTIONS_PANEL_NAME);
    }

    private void showPanel(final String panelName) {
        this.currentCardPanel = panelName;
        JPanel panel = getPanel();
        cardLayout.show(panel, panelName);
        panel.repaint();
        requestFocusInWindow();
    }

    public void updateDebriefing(final PostRoundSanctionUpdateEvent event) {
        Runnable runnable = () -> {
            showInstructionsPanel();
        };
        SwingUtilities.invokeLater(runnable);
    }

    public void showExitInstructions() {
        showDebriefing(dataModel.getClientData(), true);
        showInstructionsPanel();
    }

    public synchronized void endRound(final EndRoundEvent event) {
        if (robotWorker != null) {
            robotWorker.cancel(true);
            robotWorker = null;
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (inRoundChatPanel != null) {
                    inRoundChatPanel.disableChat();
                }
                RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
                if (roundConfiguration.isPostRoundSanctioningEnabled()) {
                    // add sanctioning text and slap the PostRoundSanctioningPanel in
                    PostRoundSanctioningPanel panel = new PostRoundSanctioningPanel(event, roundConfiguration, client);
                    panel.setName(POST_ROUND_SANCTIONING_PANEL_NAME);
                    add(panel);
                    showPanel(POST_ROUND_SANCTIONING_PANEL_NAME);
                } else {
                    instructionsEditorPane.setText("Waiting for updated round totals from the server...");
                    showInstructionsPanel();
                }
                showDebriefing(event.getClientData(), false);
            });
        } catch (InterruptedException ignored) {
            ignored.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            logger.severe(e.getMessage());
        }
    }

    public void initializeChatPanel() {
        SwingUtilities.invokeLater(() -> {
            // FIXME: reduce duplicated code in dedicated round chat and in-round chat.
            ChatPanel chatPanel = getChatPanel();
            chatPanel.initialize(dataModel);
            showPanel(CHAT_PANEL_NAME);
            // startChatTimer();
        });
    }

    public void add(JComponent component) {
        getPanel().add(component, component.getName());
    }

    @Override
    public JPanel getPanel() {
        return mainPanel;
    }

    public JPanel getSurveyIdPanel() {
        if (surveyIdPanel == null) {
            surveyIdPanel = new SurveyIdPanel(client);
        }
        return surveyIdPanel;
    }

    @Override
    public void dispose() {
        // no-op, nothing to dispose.
    }

    @Override
    public void requestFocusInWindow() {
        mainPanel.requestFocusInWindow();

    }

    public void surveyIdSubmitted() {
        setInstructions(dataModel.getRoundConfiguration().getWelcomeInstructions());
        showInstructionsPanel();
    }

    public void strategyNominationSubmitted() {
        RoundConfiguration roundConfiguration = dataModel.getRoundConfiguration();
        setInstructions(roundConfiguration.getSubmittedVoteInstructions());
        showInstructionsPanel();
    }

    public void showNextInstructions() {
        screenNumber++;
        showInstructions(screenNumber);
    }
}
