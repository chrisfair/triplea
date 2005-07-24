/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/*
 * MapPanel.java
 * 
 * Created on November 5, 2001, 1:54 PM
 */

package games.strategy.triplea.ui;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.ChangeAttatchmentChange;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.events.GameDataChangeListener;
import games.strategy.engine.data.events.TerritoryListener;
import games.strategy.triplea.Constants;
import games.strategy.triplea.image.MapImage;
import games.strategy.triplea.image.UnitImageFactory;
import games.strategy.triplea.ui.screen.SmallMapImageManager;
import games.strategy.triplea.ui.screen.Tile;
import games.strategy.triplea.ui.screen.TileManager;
import games.strategy.triplea.ui.screen.UnitsDrawer;
import games.strategy.triplea.util.Stopwatch;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeperator;
import games.strategy.ui.ImageScrollerLargeView;
import games.strategy.ui.ScrollListener;
import games.strategy.ui.Util;
import games.strategy.util.ListenerList;
import games.strategy.util.Tuple;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

/**
 * Responsible for drawing the large map and keeping it updated.
 * 
 * @author Sean Bridges
 */
public class MapPanel extends ImageScrollerLargeView
{
    private static Logger s_logger = Logger.getLogger(MapPanel.class.getName());
    
    private ListenerList<MapSelectionListener> m_mapSelectionListeners = new ListenerList<MapSelectionListener>();
    private ListenerList<UnitSelectionListener> m_unitSelectionListeners = new ListenerList<UnitSelectionListener>();

    private GameData m_data;
    private Territory m_currentTerritory; //the territory that the mouse is
    // currently over
    //could be null
    private MapPanelSmallView m_smallView;

    private SmallMapImageManager m_smallMapImageManager;
    
    //keep a reference to the images from the last paint to
    //prevent them from being gcd
    private List<Object> m_images = new ArrayList<Object>();
    
    private RouteDescription m_routeDescription;

    private final TileManager m_tileManager = new TileManager();
    
    private final BackgroundDrawer m_backgroundDrawer = new BackgroundDrawer(this);
    
    private BufferedImage m_mouseShadowImage = null;
    
    /** Creates new MapPanel */
    public MapPanel(Dimension imageDimensions, GameData data, MapPanelSmallView smallView) throws IOException
    {
        super(imageDimensions);
        
        Thread t = new Thread(m_backgroundDrawer, "Map panel background drawer");
        t.setDaemon(true);
        t.start();
        
        setDoubleBuffered(false);
       

        m_smallView = smallView;
        m_smallMapImageManager = new SmallMapImageManager(smallView, MapImage.getInstance().getSmallMapImage(),  m_tileManager);
        
        setGameData(data);

        this.addMouseListener(MOUSE_LISTENER);
        this.addMouseMotionListener(MOUSE_MOTION_LISTENER);
        
        this.addScrollListener(new ScrollListener()
        {

            public void scrolled(int x, int y)
            {
                repaint();
            }
            
        });
        m_tileManager.createTiles(new Rectangle(imageDimensions), data, MapData.getInstance());
        m_tileManager.resetTiles(data, MapData.getInstance());
    } 
     
    GameData getData()
    {
        return m_data;
    }

    // Beagle Code used to chnage map skin
    public void changeImage(Dimension newDimensions)
    {
 
       
        super.setDimensions(newDimensions);
        m_tileManager.createTiles(new Rectangle(newDimensions), m_data, MapData.getInstance());
        m_tileManager.resetTiles(m_data, MapData.getInstance());

        
    }

    public boolean isShowing(Territory territory)
    {

        Point territoryCenter = MapData.getInstance().getCenter(territory);

        Rectangle screenBounds = new Rectangle(super.getXOffset(), super.getYOffset(), super.getWidth(), super.getHeight());
        return screenBounds.contains(territoryCenter);

    }

    public void centerOn(Territory territory)
    {

        if (territory == null)
            return;

        Point p = MapData.getInstance().getCenter(territory);

        //when centering dont want the map to wrap around,
        //eg if centering on hawaii
        super.setTopLeft(p.x - (getWidth() / 2), p.y - (getHeight() / 2));
    }

    public void setRoute(Route route)
    {
        setRoute(route, null, null);
    }

    /**
     * Set the route, could be null.
     */
    public void setRoute(Route route, Point start, Point end)
    {
        if (route == null)
        {
            m_routeDescription = null;
            repaint();
            return;
        }
        RouteDescription newVal = new RouteDescription(route, start, end);
        if (m_routeDescription != null && m_routeDescription.equals(newVal))
        {
            return;
        }

        m_routeDescription = newVal;
        repaint();

    }

    public void addMapSelectionListener(MapSelectionListener listener)
    {

        m_mapSelectionListeners.add(listener);
    }

    public void removeMapSelectionListener(MapSelectionListener listener)
    {
        m_mapSelectionListeners.remove(listener);
    }

    private void notifyTerritorySelected(Territory t, MouseEvent me)
    {

        Iterator<MapSelectionListener> iter = m_mapSelectionListeners.iterator();

        while (iter.hasNext())
        {
            MapSelectionListener msl = iter.next();
            msl.territorySelected(t, me);
        }
    }

    private void notifyMouseMoved(Territory t, MouseEvent me)
    {

        Iterator<MapSelectionListener> iter = m_mapSelectionListeners.iterator();

        while (iter.hasNext())
        {
            MapSelectionListener msl = iter.next();
            msl.mouseMoved(t, me);
        }
    }

    private void notifyMouseEntered(Territory t)
    {

        Iterator<MapSelectionListener> iter = m_mapSelectionListeners.iterator();

        while (iter.hasNext())
        {
            MapSelectionListener msl = iter.next();
            msl.mouseEntered(t);
        }
    }

    
    public void addUnitSelectionListener(UnitSelectionListener listener)
    {
        m_unitSelectionListeners.add(listener);
    }
    
    public void removeUnitSelectionListener(UnitSelectionListener listener)
    {
        m_unitSelectionListeners.remove(listener);
    }

    public void notifyUnitSelected(List<Unit> units, Territory t, MouseEvent me)
    {
        for(UnitSelectionListener listener : m_unitSelectionListeners)
        {
            listener.unitsSelected(units,t, me);
        }
            
    }
    
    private Territory getTerritory(int x, int y)
    {
        String name = MapData.getInstance().getTerritoryAt(normalizeX(x), y);
        if (name == null)
            return null;
        return m_data.getMap().getTerritory(name);
    }

    private int normalizeX(int x)
    {
        int imageWidth = (int) getImageDimensions().getWidth();
        if (x < 0)
            x += imageWidth;
        else if (x > imageWidth)
            x -= imageWidth;
        return x;
    }

    public void resetMap()
    {
        
       m_tileManager.resetTiles(m_data, MapData.getInstance());
       
       SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                repaint();
            }
        });
       
       m_smallMapImageManager.update(m_data, MapData.getInstance());
       
    }
    
    
    

    private MouseListener MOUSE_LISTENER = new MouseAdapter()
    {

        public void mouseReleased(MouseEvent e)
        {
            Territory terr = getTerritory(e.getX() + getXOffset(), e.getY() + getYOffset());
            if (terr != null)
                notifyTerritorySelected(terr, e);
            
            if(!m_unitSelectionListeners.isEmpty())
            {
                int x = normalizeX(e.getX() + getXOffset());
                int y =  e.getY() + getYOffset();
                Tuple<Territory, List<Unit>> tuple = m_tileManager.getUnitsAtPoint(x,y, m_data);
                
                notifyUnitSelected(tuple.getSecond(), tuple.getFirst(), e );
            }
            
        }

    };

    private final MouseMotionListener MOUSE_MOTION_LISTENER = new MouseMotionAdapter()
    {

        public void mouseMoved(MouseEvent e)
        {

            Territory terr = getTerritory(e.getX() + getXOffset(), e.getY() + getYOffset());
            //we can use == here since they will be the same object.
            //dont use .equals since we have nulls
            if (terr != m_currentTerritory)
            {
                m_currentTerritory = terr;
                notifyMouseEntered(terr);
            }

            notifyMouseMoved(terr, e);
        }
    };

    public void updateCounties(Collection<Territory> countries)
    {
        m_tileManager.updateTerritories(countries, m_data, MapData.getInstance());
        m_smallMapImageManager.update(m_data, MapData.getInstance());
        m_smallView.repaint();
        repaint();
    }

    public void setGameData(GameData data)
    {
        //clean up any old listeners
        if (m_data != null)
        {
            m_data.removeTerritoryListener(TERRITORY_LISTENER);
            m_data.removeDataChangeListener(TECH_UPDATE_LISTENER);
        }

        m_data = data;
        m_data.addTerritoryListener(TERRITORY_LISTENER);
        
        m_data.addDataChangeListener(TECH_UPDATE_LISTENER);
        
        //stop painting in the background
        m_backgroundDrawer.setTiles(Collections.<Tile>emptySet());
        
        m_tileManager.resetTiles(m_data, MapData.getInstance());

    }

    private final TerritoryListener TERRITORY_LISTENER = new TerritoryListener()
    {

        public void unitsChanged(Territory territory)
        {
            updateCounties(Collections.singleton(territory));
            repaint();
        }

        public void ownerChanged(Territory territory)
        {
            m_smallMapImageManager.updateTerritoryOwner(territory, m_data, MapData.getInstance());
            updateCounties(Collections.singleton(territory));
            repaint();
        }
    };


    private final GameDataChangeListener TECH_UPDATE_LISTENER = new GameDataChangeListener()
    {

        public void gameDataChanged(Change aChange)
        {

            //find the players with tech changes
            Set<PlayerID> playersWithTechChange = new HashSet<PlayerID>();
            getPlayersWithTechChanges(aChange, playersWithTechChange);

            if (playersWithTechChange.isEmpty())
                return;

            m_tileManager.resetTiles(m_data, MapData.getInstance());
            repaint();
        }

       
        private void getPlayersWithTechChanges(Change aChange, Set<PlayerID> players)
        {

            if (aChange instanceof CompositeChange)
            {
                CompositeChange composite = (CompositeChange) aChange;
                Iterator iter = composite.getChanges().iterator();
                while (iter.hasNext())
                {
                    Change item = (Change) iter.next();
                    getPlayersWithTechChanges(item, players);

                }
            } else
            {
                if (aChange instanceof ChangeAttatchmentChange)
                {
                    ChangeAttatchmentChange changeAttatchment = (ChangeAttatchmentChange) aChange;
                    if (changeAttatchment.getAttatchmentName().equals(Constants.TECH_ATTATCHMENT_NAME))
                    {
                        players.add((PlayerID) changeAttatchment.getAttatchedTo());
                    }
                }

            }
        }

    };

        
    public void paint(Graphics g)
    {
        super.paint(g);
        List<Tile> images = new ArrayList<Tile>();
        List<Tile> undrawnTiles = new ArrayList<Tile>();
        
        Stopwatch stopWatch = new Stopwatch(s_logger, Level.FINER, "Paint");

        //make sure we use the same data for the entire paint
        final GameData data = m_data;
        
        data.acquireChangeLock();
        try
        {
	        //handle wrapping off the screen to the left
	        if(m_x < 0)
	        {
	            Rectangle leftBounds = new Rectangle(m_dimensions.width + m_x, m_y, -m_x, getHeight());
	            drawTiles(g, images, data, leftBounds,0, undrawnTiles);
	        }
	        
	        //handle the non overlap
		    Rectangle mainBounds = new Rectangle(m_x, m_y, getWidth(), getHeight());
		    drawTiles(g, images, data, mainBounds,0, undrawnTiles);
	        
	        double leftOverlap = m_x + getWidth() - m_dimensions.getWidth();
	        //handle wrapping off the screen to the left
	        if(leftOverlap > 0)
	        {
	            Rectangle rightBounds = new Rectangle(0 , m_y, (int) leftOverlap, getHeight());
	            drawTiles(g, images, data, rightBounds, leftOverlap, undrawnTiles);
	        }
	
	        
	        MapRouteDrawer.drawRoute((Graphics2D) g, m_routeDescription, this);
	        
            if(m_routeDescription != null && m_mouseShadowImage != null && m_routeDescription.getEnd() != null)
            {
                ((Graphics2D) g).drawImage(m_mouseShadowImage, (int)  m_routeDescription.getEnd().getX() - getXOffset(), (int) m_routeDescription.getEnd().getY() - getYOffset(), this);
            }
            
            //used to keep strong references to what is on the screen so it wont be garbage collected
            //other references to the images are week references
	        m_images.clear();
	        m_images.addAll(images);
        }
        finally
        {
            data.releaseChangeLock();
        }
                
        //draw the tiles nearest us first
        //then draw farther away
        drawNearbyTiles(undrawnTiles, 30, true);
        drawNearbyTiles(undrawnTiles, 257, true);
        //when we are this far away, dont force the tiles to stay in memroy
        drawNearbyTiles(undrawnTiles, 513, false);
        drawNearbyTiles(undrawnTiles, 767, false);
        
        
        m_backgroundDrawer.setTiles(undrawnTiles);
        
        
        
        stopWatch.done();
        
    }

   
    /**
     * If we have nothing left undrawn, draw the tiles within preDrawMargin of us, optionally
     * forcing the tiles to remain in memory. 
     */
    private void drawNearbyTiles(List<Tile> undrawnTiles, int preDrawMargin, boolean forceInMemory)
    {
        //draw tiles near us if we have nothing left to draw
        //that way when we scroll slowly we wont notice a glitch
        if(undrawnTiles.isEmpty())
        {
            
            Rectangle extendedBounds = new Rectangle( Math.max(m_x -preDrawMargin, 0),Math.max(m_y -preDrawMargin, 0), getWidth() + (2 * preDrawMargin),  getHeight() + (2 * preDrawMargin));
            Iterator tiles = m_tileManager.getTiles(extendedBounds).iterator();

            while (tiles.hasNext())
            {
                Tile tile = (Tile) tiles.next();
                if(tile.isDirty())
                {
                    undrawnTiles.add(tile);
                }
                else if(forceInMemory)
                {
                    m_images.add(tile.getRawImage());
                }
            }
            
            
        }
    }

    private void drawTiles(Graphics g, List<Tile> images, final GameData data, Rectangle bounds, double overlap, List<Tile> undrawn)
    {
        List tileList = m_tileManager.getTiles(bounds);
        Iterator tiles = tileList.iterator();

        if(overlap != 0)
        {
            bounds.translate((int) (overlap - getWidth()), 0);    
        }
        
        while (tiles.hasNext())
        {
            Image img = null;
            Tile tile = (Tile) tiles.next();
            if(tile.isDirty())
            {
                //take what we can get to avoid screen flicker
                undrawn.add(tile);
                img = tile.getRawImage();
                
            }
            else
            {
	            img = tile.getImage(data, MapData.getInstance());
	            images.add(tile);
            }
            if(img != null)
                g.drawImage(img, tile.getBounds().x -bounds.x, tile.getBounds().y - m_y, this);
        }
    }

    public Image getTerritoryImage(Territory territory)
    {
        return m_tileManager.createTerritoryImage(territory, m_data,MapData.getInstance());
    }



    /**
     * 
     */
    public void initSmallMap()
    {
        Iterator territories = m_data.getMap().getTerritories().iterator();
        
        while (territories.hasNext())
        {
            Territory territory = (Territory) territories.next();
            m_smallMapImageManager.updateTerritoryOwner(territory, m_data, MapData.getInstance());
            
        }
        
        m_smallMapImageManager.update(m_data, MapData.getInstance());
        
    }  
    
    public void finalize()
    {
        m_backgroundDrawer.stop();
    }

    
    public void setMouseShadowUnits(Collection<Unit> units)
    {
        if(units == null || units.isEmpty())
        {
            m_mouseShadowImage = null;
            SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            repaint();
                        }
                    
                    });
            return;
        }
        
        Set<UnitCategory> categories =  UnitSeperator.categorize(units);
        
        final int xSpace = 5;
        
        BufferedImage img = Util.createImage( (categories.size() + xSpace) * UnitImageFactory.UNIT_ICON_WIDTH, UnitImageFactory.UNIT_ICON_HEIGHT, true); 
        Graphics2D g = (Graphics2D) img.getGraphics();
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f) );
        
        Rectangle bounds = new Rectangle(0,0,0,0);
        
        
        int i = 0;
        for(UnitCategory category : categories)
        {
            Point place = new Point( i * (UnitImageFactory.UNIT_ICON_WIDTH + xSpace), 0);
            UnitsDrawer drawer = new UnitsDrawer(category.getUnits().size(), category.getType().getName(), 
                    category.getOwner().getName(), place,category.getDamaged(), false, "" );
            drawer.draw(bounds, m_data, g, MapData.getInstance());
            i++;
        }
        m_mouseShadowImage = img;
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                repaint();
            }
        
        });
    }
    
    public void setTerritoryOverlay(Territory territory, Color color, float opaqueness)
    {
        m_tileManager.setTerritoryOverlay(territory, color, opaqueness, m_data, MapData.getInstance() );
    }

    public void clearTerritoryOverlay(Territory territory)
    {
        m_tileManager.clearTerritoryOverlay(territory, m_data, MapData.getInstance());
    }

    
}

class RouteDescription
{

    private final Route m_route;
    private final Point m_start;
    private final Point m_end;

    public RouteDescription(Route route, Point start, Point end)
    {
        m_route = route;
        m_start = start;
        m_end = end;
    }

    public boolean equals(Object o)
    {
        if (o == null)
            return false;
        if (o == this)
            return true;
        RouteDescription other = (RouteDescription) o;

        if (m_start == null && other.m_start != null || other.m_start == null && m_start != null
                || (m_start != other.m_start && !m_start.equals(other.m_start)))
            return false;

        if (m_route == null && other.m_route != null || other.m_route == null && m_route != null
                || (m_route != other.m_route && !m_route.equals(other.m_route)))
            return false;

        if (m_end == null && other.m_end != null || other.m_end == null && m_end != null)
            return false;

        //we dont want to be updating for every small change,
        //if the end points are close enough, they are close enough
        if (other.m_end == null && this.m_end != null)
            return false;
        if (other.m_end != null && this.m_end == null)
            return false;

        int xDiff = m_end.x - other.m_end.x;
        xDiff *= xDiff;
        int yDiff = m_end.y - other.m_end.y;
        yDiff *= yDiff;
        int endDiff = (int) Math.sqrt(xDiff + yDiff);

        return endDiff < 6;

    }

    public Route getRoute()
    {
        return m_route;
    }

    public Point getStart()
    {
        return m_start;
    }

    public Point getEnd()
    {
        return m_end;
    }
    
  
}

class BackgroundDrawer implements Runnable
{
    private final List<Tile> m_tiles = new ArrayList<Tile>();
    private final Object m_mutex = new Object();
    private boolean m_active = true;
    private final MapPanel m_mapPanel;
    
    BackgroundDrawer(MapPanel panel)
    {
        m_mapPanel = panel;
    }
    
    public void stop()
    {
        m_active = false;
        synchronized(m_mutex)
        {
            m_tiles.clear();
            m_mutex.notifyAll();
        }
    }
    
    public void setTiles(Collection<Tile> aCollection) 
    {
	      synchronized(m_mutex)
	      {
	          m_tiles.clear();
	          m_tiles.addAll(aCollection);
	          if(!m_tiles.isEmpty())
	              m_mutex.notifyAll();
	      }
    }
    
    public void run()
    {
        while(m_active)
        {
            Tile tile ;
            synchronized(m_mutex)
            {
                if(m_tiles.isEmpty())
                {
                    //wait for more tiles
                    try
                    {
                        m_mutex.wait();
                    } catch (InterruptedException e)
                    {

                    }
                    
                    continue;
                }
                else
                {
                    tile = m_tiles.remove(0);
                }
                    
                GameData data = m_mapPanel.getData(); 
                data.acquireChangeLock();
                try
                {
	                tile.getImage(data, MapData.getInstance());
	                //update the main map after we update each tile
	                //this allows the tiles to be shown as soon as they are updated
                }
                finally
                {
                    data.releaseChangeLock();
                }
                SwingUtilities.invokeLater(new Runnable()
                {
                   public void run()
                   { 
                       m_mapPanel.repaint();
                   }
                });
            }
            
        }
    }
    
    
    
}
