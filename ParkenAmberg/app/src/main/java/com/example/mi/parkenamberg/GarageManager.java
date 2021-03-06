package com.example.mi.parkenamberg;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

class GarageManager
{
  Boolean Initialized = false;
  private Garage[] garages = new Garage[8];
  UpdateFinishedCallback UpdateCallback;
  private Handler internalUpdateHandler = new Handler();
  private ParkActivity context;
  Date lastUpdate;

  GarageManager(ParkActivity mainActivity)
  {
    context = mainActivity;

    garages[0] = (new Garage(new LatLng(49.4416168,11.8590742), mainActivity.getString(R.string.garage0), 1));
    garages[1] = (new Garage(new LatLng(49.4464226,11.8547069), mainActivity.getString(R.string.garage1), 2));
    garages[2] = (new Garage(new LatLng(49.4479491,11.8527369), mainActivity.getString(R.string.garage2), 3));
    garages[3] = (new Garage(new LatLng(49.4487832,11.8565285), mainActivity.getString(R.string.garage3), 4));
    garages[4] = (new Garage(new LatLng(49.4419695,11.857748), mainActivity.getString(R.string.garage4), 5));
    garages[5] = (new Garage(new LatLng(49.44438,11.86432), mainActivity.getString(R.string.garage5), 6));
    garages[6] = (new Garage(new LatLng(49.44145,11.85975), mainActivity.getString(R.string.garage6), 7));
    garages[7] = (new Garage(new LatLng(49.44748,11.86143), mainActivity.getString(R.string.garage7), 8));

    Runnable runnable = new Runnable()
    {
      @Override
      public void run()
      {
        Log.d("GarageManager", "Update running...");
        Update();
        internalUpdateHandler.postDelayed(this, 60000);
      }
    };
    internalUpdateHandler.postDelayed(runnable, 10000);

    loadSettings();
  }

  private void loadSettings()
  {
    SharedPreferences settings = context.getPreferences(Context.MODE_PRIVATE);

    for (Garage g : garages)
    {
      g.setShow(settings.getBoolean(g.getName(), false));
    }
  }

  interface UpdateFinishedCallback
  {
    void onFinished(Boolean success);
  }

  ArrayList<Garage> GetGarages()
  {
    return new ArrayList<>(Arrays.asList(garages));
  }

  void Update()
  {
    HTTPRequest.TaskListener listener = new HTTPRequest.TaskListener()
    {
      @Override
      public void onFinished(Document result)
      {
        Boolean success = UpdatePlaetze(result);
        Initialized = success;
        if (UpdateCallback != null)
        {
          UpdateCallback.onFinished(success);
        }
      }
    };

    HTTPRequest task = new HTTPRequest(listener, context);

    task.execute();
  }

  /**
   * Updates parking lot numbers
   */
  private Boolean UpdatePlaetze(Document doc)
  {
    try
    {
      if(doc == null) return false;
      NodeList nodes = doc.getElementsByTagName("Parkhaus");
      for (int i = 0; i < nodes.getLength(); i++)
      {
        Element element = (Element) nodes.item(i);
        NodeList idNodes = element.getElementsByTagName("ID");
        if (idNodes.getLength() > 0)
        {
          int id = Integer.parseInt(idNodes.item(0).getFirstChild().getNodeValue());
          NodeList currentNodes, maxNodes, closedNodes, trendNodes;
          maxNodes = element.getElementsByTagName("Gesamt");
          currentNodes = element.getElementsByTagName("Aktuell");
          closedNodes = element.getElementsByTagName("Geschlossen");
          trendNodes = element.getElementsByTagName("Trend");
          Log.d(garages[id-1].getName(), "" +garages[id-1].getLocation());
          if (maxNodes.getLength() > 0)
          {
            Element max = (Element) maxNodes.item(0);
            garages[id - 1].setMaxPlaetze(Integer.parseInt(max.getFirstChild().getNodeValue()));
          }
          if (currentNodes.getLength() > 0)
          {
            Element current = (Element) currentNodes.item(0);
            garages[id - 1].setCurPlaetze(Integer.parseInt(current.getFirstChild().getNodeValue()));
          }
          if (closedNodes.getLength() > 0)
          {
            Element closed = (Element) closedNodes.item(0);
            garages[id - 1].closed = Integer.parseInt(closed.getFirstChild().getNodeValue()) != 0;
          }
          if(trendNodes.getLength() > 0)
          {
            Element trend = (Element) trendNodes.item(0);
            garages[id - 1].setTrend(Integer.parseInt(trend.getFirstChild().getNodeValue()));
          }
        }
      }
    } catch (Exception e)
    {
      //dam, son
      Log.d("", Log.getStackTraceString(e));

      return false;
    }
    lastUpdate = new Date();
    return true;
  }

  /**
   * Returns the garage with the name
   *
   * @param name of garage
   * @return returns the garage or null
   */
  Garage GetGarageByName(String name)
  {
    for (Garage g : garages)
    {
      if (g.getName().equals(name))
      {
        return g;
      }
    }

    return null;
  }

  /**
   * Returns the garage with the id
   *
   * @param id of garage
   * @return returns the garage or null
   */
  Garage GetGarageById(int id)
  {
    for (Garage g : garages)
    {
      if (g.getId() == id)
      {
        return g;
      }
    }

    return null;
  }

  private static class HTTPRequest extends AsyncTask<String, Void, Document>
  {
    interface TaskListener
    {
      void onFinished(Document result);
    }

    // This is the reference to the associated listener
    private final TaskListener taskListener;
    private ParkActivity context;

    HTTPRequest(TaskListener listener, ParkActivity act)
    {
      // The listener reference is passed in through the constructor
      this.taskListener = listener;
      context = act;
    }

    @Override
    protected void onPostExecute(Document result)
    {
      super.onPostExecute(result);

      // In onPostExecute we check if the listener is valid
      if (this.taskListener != null)
      {

        // And if it is we call the callback function on it.
        this.taskListener.onFinished(result);
      }
    }

    @Override
    protected Document doInBackground(String... foobar)
    {
      Document doc = null;
      try
      {
        URL url = new URL("http://parken.amberg.de/wp-content/uploads/pls/pls.xml");
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(1000);
        conn.setReadTimeout(1000);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.parse(conn.getInputStream());
      } catch (Exception e)
      {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable()
        {
          @Override
          public void run()
          {
            for (int i = 0; i < 2; i++)
            {
              Toast.makeText(context, R.string.noInternet, Toast.LENGTH_LONG).show();
            }
          }
        });
      }
      return doc;
    }
  }
}