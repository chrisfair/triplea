package games.strategy.engine.chat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import games.strategy.ui.SwingAction;
import games.strategy.net.INode;

public class ChatPlayerPanel extends JPanel implements IChatListener {
  private static final long serialVersionUID = -3153022965393962945L;
  private static final Icon s_ignoreIcon;

  static {
    final URL ignore = ChatPlayerPanel.class.getResource("ignore.png");
    if (ignore == null) {
      throw new IllegalStateException("Could not find ignore icon");
    }
    Image img;
    try {
      img = ImageIO.read(ignore);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    s_ignoreIcon = new ImageIcon(img);
  }

  private JList<INode> m_players;
  private DefaultListModel<INode> m_listModel;
  private Chat m_chat;
  private final Set<String> m_hiddenPlayers = new HashSet<>();
  private final IStatusListener m_statusListener;
  // if our renderer is overridden
  // we do not set this directly on the JList,
  // instead we feed it the node name and staus as a string
  private ListCellRenderer<Object> m_setCellRenderer = new DefaultListCellRenderer();
  private final List<IPlayerActionFactory> m_actionFactories = new ArrayList<>();

  public ChatPlayerPanel(final Chat chat) {
    createComponents();
    layoutComponents();
    setupListeners();
    setWidgetActivation();
    m_statusListener = new IStatusListener() {
      @Override
      public void statusChanged(final INode node, final String newStatus) {
        repaint();
      }
    };
    setChat(chat);
  }

  public void addHiddenPlayerName(final String name) {
    m_hiddenPlayers.add(name);
  }

  public void shutDown() {
    if (m_chat != null) {
      m_chat.removeChatListener(this);
      m_chat.getStatusManager().removeStatusListener(m_statusListener);
    }
    m_chat = null;
    this.setVisible(false);
    this.removeAll();
  }

  public void setChat(final Chat chat) {
    if (m_chat != null) {
      m_chat.removeChatListener(this);
      m_chat.getStatusManager().removeStatusListener(m_statusListener);
    }
    m_chat = chat;
    if (chat != null) {
      chat.addChatListener(this);
      m_chat.getStatusManager().addStatusListener(m_statusListener);
    } else {
      // empty our player list
      updatePlayerList(Collections.emptyList());
    }
    repaint();
  }

  /**
   * set minimum size based on players (number and max name length) and distribution to playerIDs
   */
  private void setDynamicPreferredSize() {
    final List<INode> onlinePlayers = m_chat.getOnlinePlayers();
    int maxNameLength = 0;
    final FontMetrics fontMetrics = this.getFontMetrics(UIManager.getFont("TextField.font"));
    for (final INode iNode : onlinePlayers) {
      maxNameLength = Math.max(maxNameLength, fontMetrics.stringWidth(iNode.getName()));
    }
    int iconCounter = 0;
    if (m_setCellRenderer instanceof PlayerChatRenderer) {
      iconCounter = ((PlayerChatRenderer) m_setCellRenderer).getMaxIconCounter();
    }
    setPreferredSize(new Dimension(maxNameLength + 40 + iconCounter * 14, 80));
  }

  private void createComponents() {
    m_listModel = new DefaultListModel<>();
    m_players = new JList<>(m_listModel);
    m_players.setFocusable(false);
    m_players.setCellRenderer(new ListCellRenderer<INode>() {
      @Override
      public Component getListCellRendererComponent(JList<? extends INode> list, INode node, int index, boolean isSelected, boolean cellHasFocus) {
        if (m_setCellRenderer == null) {
          return new JLabel();
        }
        final DefaultListCellRenderer renderer;
        if (m_setCellRenderer instanceof PlayerChatRenderer) {
          renderer = (DefaultListCellRenderer) m_setCellRenderer.getListCellRendererComponent(list, node, index,
              isSelected, cellHasFocus);
        } else {
          renderer = (DefaultListCellRenderer) m_setCellRenderer.getListCellRendererComponent(list,
              getDisplayString(node), index, isSelected, cellHasFocus);
        }
        if (m_chat.isIgnored(node)) {
          renderer.setIcon(s_ignoreIcon);
        }
        return renderer;
      }
    });
  }

  private void layoutComponents() {
    setLayout(new BorderLayout());
    add(new JScrollPane(m_players), BorderLayout.CENTER);
  }

  private void setupListeners() {
    m_players.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        mouseOnPlayersList(e);
      }

      @Override
      public void mousePressed(final MouseEvent e) {
        mouseOnPlayersList(e);
      }

      @Override
      public void mouseReleased(final MouseEvent e) {
        mouseOnPlayersList(e);
      }
    });
    m_actionFactories.add(new IPlayerActionFactory() {
      @Override
      public List<Action> mouseOnPlayer(final INode clickedOn) {
        // you can't slap or ignore yourself
        if (clickedOn.equals(m_chat.getLocalNode())) {
          return Collections.emptyList();
        }
        final boolean isIgnored = m_chat.isIgnored(clickedOn);
        final Action ignore = SwingAction.of( isIgnored ? "Stop Ignoring" : "Ignore", e-> {
            m_chat.setIgnored(clickedOn, !isIgnored);
            repaint();
        });
        final Action slap = new AbstractAction("Slap " + clickedOn.getName()) {
          private static final long serialVersionUID = -5514772068903406263L;

          @Override
          public void actionPerformed(final ActionEvent event) {
            m_chat.sendSlap(clickedOn.getName());
          }
        };
        return Arrays.asList(slap, ignore);
      }
    });
  }

  private void setWidgetActivation() {}

  /**
   * The renderer will be passed in a string
   */
  public void setPlayerRenderer(final ListCellRenderer<Object> renderer) {
    m_setCellRenderer = renderer;
    setDynamicPreferredSize();
  }

  private void mouseOnPlayersList(final MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }
    final int index = m_players.locationToIndex(e.getPoint());
    if (index == -1) {
      return;
    }
    final INode player = m_listModel.get(index);
    final JPopupMenu menu = new JPopupMenu();
    boolean hasActions = false;
    for (final IPlayerActionFactory factory : m_actionFactories) {
      final List<Action> actions = factory.mouseOnPlayer(player);
      if (actions != null && !actions.isEmpty()) {
        if (hasActions) {
          menu.addSeparator();
        }
        hasActions = true;
        for (final Action a : actions) {
          menu.add(a);
        }
      }
    }
    if (hasActions) {
      menu.show(m_players, e.getX(), e.getY());
    }
  }

  /**
   * @param players
   *        - a collection of Strings representing player names.
   */
  @Override
  public synchronized void updatePlayerList(final Collection<INode> players) {
    final Runnable runner = new Runnable() {
      @Override
      public void run() {
        m_listModel.clear();
        for (final INode name : players) {
          if (!m_hiddenPlayers.contains(name.getName())) {
            m_listModel.addElement(name);
          }
        }
      }
    };
    // invoke in the swing event thread
    if (SwingUtilities.isEventDispatchThread()) {
      runner.run();
    } else {
      SwingUtilities.invokeLater(runner);
    }
  }

  @Override
  public void addMessageWithSound(final String message, final String from, final boolean thirdperson,
      final String sound) {}

  @Override
  public void addMessage(final String message, final String from, final boolean thirdperson) {}

  private String getDisplayString(final INode node) {
    if (m_chat == null) {
      return "";
    }
    String extra = "";
    final String notes = m_chat.getNotesForNode(node);
    if (notes != null && notes.length() > 0) {
      extra = extra + notes;
    }
    String status = m_chat.getStatusManager().getStatus(node);
    final StringBuilder statusSB = new StringBuilder("");
    if (status != null && status.length() > 0) {
      if (status.length() > 25) {
        status = status.substring(0, 25);
      }
      for (int i = 0; i < status.length(); i++) {
        final char c = status.charAt(i);
        // skip combining characters
        if (c >= '\u0300' && c <= '\u036F') {
          continue;
        }
        statusSB.append(c);
      }
      extra = extra + " (" + statusSB.toString() + ")";
    }
    if (extra.length() == 0) {
      return node.getName();
    }
    return node.getName() + extra;
  }

  @Override
  public void addStatusMessage(final String message) {}

  /**
   * Add an action factory that will be used to populate the pop up meny when
   * right clicking on a player in the chat panel.
   */
  public void addActionFactory(final IPlayerActionFactory actionFactory) {
    m_actionFactories.add(actionFactory);
  }

  public void remove(final IPlayerActionFactory actionFactory) {
    m_actionFactories.remove(actionFactory);
  }
}
