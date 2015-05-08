package PicassoEngine;

import java.awt.*;
import java.awt.image.VolatileImage;
import java.util.*;

public class Renderer {
	private boolean render = true;
	private Frame frame;
	private Camera camera;
	Graphics2D context;
	private int FPS = 0;
	private long lastShown = 0;
	
	public Renderer(Frame frame) {
		this.frame = frame;
	}
	
	public void startRender() {
		render = true;
	}
	
	public void stopRender() {
		render = false;
	}
	
	public void render(Graphics graphics) {
		if (render) {
			// Set the time at the very start of the frame
			long lastLoopTime = System.nanoTime();
			
			// Initialize frame and context
			VolatileImage frame = this.frame.getFrame().createVolatileImage(this.frame.getFrame().getWidth(), this.frame.getFrame().getHeight());
			context = frame.createGraphics();
			
			// Physics - To be implemented
			
			// Update - Run game logic
			this.frame.getScene().callUpdate();
			
			// LateUpdate - Run additional game logic
			this.frame.getScene().callLateUpdate();
			
			// Reset mouse position
			Input.resetMouseMovement();
			this.frame.getCanvas().getMouseListener().recenterMouse();
			
			// Render the camera view to the context
			drawCameraView();
			
			// Paint frame to canvas
			graphics.drawImage(frame, 0, 0, this.frame.getFrame());
			
			// Repaint the window which then calls the next frame
			this.frame.getFrame().repaint();
			
			// Slow down if frames are rendering too fast
			if ((System.nanoTime() - lastLoopTime) / 1000000000.0 < 0.016) {
				try {
					Thread.sleep(15 - (System.nanoTime() - lastLoopTime) / 1000000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			// Set deltaTime as the length of time in seconds that this frame took
			Time.deltaTime = (System.nanoTime() - lastLoopTime) / 1000000000.0;
			
			// Log FPS
			FPS++;
			if (lastShown == 0) {
				lastShown = System.nanoTime();
			}
			if (System.nanoTime() - lastShown >= 1000000000) {
				//System.out.println(FPS);
				lastShown = System.nanoTime();
				FPS = 0;
			}
		}
	}
	
	public void drawCameraView() {
		camera = frame.getScene().getActiveCamera();
		ArrayList<GameObject> objects = frame.getScene().getGameObjects();
		ArrayList<Model> models = new ArrayList<Model>();
		for (GameObject object : objects) {
			if (object instanceof Model) {
				models.add((Model) object);
			}
		}
		
		// All the polygons in all the models
		int totalFaces = 0;
		for (Model model : models) {
			totalFaces += model.getFaces().length;
		}
		ArrayList<Polygon2D> polygons = new ArrayList<Polygon2D>(totalFaces);
		int polygonIndex = 0;
		
		// Add every face in every model to the list of polygons
		for (Model model : models) {
			// Faces
			Face[] faces = model.getFaces();
			
			// Vertices
			Vector3[] vertices = model.getVertices();
			Vector3[] projectedVertices = new Vector3[vertices.length];
			
			// PicassoEngine.Face centers
			Vector3[] centroids = model.getFaceCenters();
			double[] centroidDepths = new double[centroids.length];
			
			// Project vertices and put them in a corresponding screen space array
			for (int vertex = 0; vertex < vertices.length; vertex++) {
				projectedVertices[vertex] = project(vertices[vertex]);
			}
			
			// Add all the vertices in all the faces in this model to a polygons ArrayList
			for (int face = 0; face < faces.length; face++) {
				boolean onScreen = false;
				int[] vertexIndexes = faces[face].getVertexIndexes();
				Vector2[] faceVertices = new Vector2[vertexIndexes.length];
				
				for (int i = 0; i < vertexIndexes.length; i++) {
					// Add the projected vertex as a screen point in the 2D screen face
					faceVertices[i] = new Vector2(projectedVertices[vertexIndexes[i]].x, projectedVertices[vertexIndexes[i]].y);
					
					// Check if it's on screen
					if (faceVertices[i].x > 0 && faceVertices[i].x < frame.getFrame().getWidth() && faceVertices[i].y > 0 && faceVertices[i].y < frame.getFrame().getHeight() && projectedVertices[vertexIndexes[i]].z > 0) {
						onScreen = true;
					}
				}
				
				if (onScreen) {
					// Add this face as a polygon in the final array
					Vector3 v1 = vertices[vertexIndexes[0]].difference(vertices[vertexIndexes[1]]);
					Vector3 v2 = vertices[vertexIndexes[2]].difference(vertices[vertexIndexes[1]]);
					double brightness = v1.cross(v2).angle(new Vector3(5, 10, 0)) / Math.PI;
					polygons.add(new Polygon2D(faceVertices, project(centroids[face]), faces[face].getColor(), brightness));
				}
			}
		}
		
		Collections.sort(polygons, new Comparator<Polygon2D>() {
			public int compare(Polygon2D p1, Polygon2D p2) {
				if (p1.getProjectedCentroid().z > p2.getProjectedCentroid().z) {
					return -1;
				} else if (p1.getProjectedCentroid().z < p2.getProjectedCentroid().z) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		
		// Draw each polygon
		for (Polygon2D polygon : polygons) {
			int[] x = new int[polygon.getPoints().length];
			int[] y = new int[x.length];
			
			for (int point = 0; point < x.length; point++) {
				x[point] = Math.round((float) polygon.getPoints()[point].x);
				y[point] = Math.round((float) polygon.getPoints()[point].y);
			}
			
			context.setColor(Color.decode("#" + polygon.getColor()));
			context.fillPolygon(x, y, x.length);
//			context.setColor(Color.black); // Set color to black for the below options
//			context.drawPolygon(x, y, x.length); // Draw wireframe outline
//			context.drawOval((int) polygon.getProjectedCentroid().x, (int) polygon.getProjectedCentroid().y, 10, 10); // Draw centroid used in z-sorting
		}
	}
	
	private Vector3 project(Vector3 point) {
		Vector3 point3d = new Vector3(point.x, point.y, point.z);
		
		Vector3 screenPoint = new Vector3();
		
		screenPoint.x = Math.cos(camera.rotation.y) * (Math.sin(camera.rotation.z) * (point3d.y - camera.position.y) + Math.cos(camera.rotation.z) * (point3d.x - camera.position.x)) - Math.sin(camera.rotation.y) * (point3d.z - camera.position.z);
		
		screenPoint.y = Math.sin(camera.rotation.x) * (Math.cos(camera.rotation.y) * (point3d.z - camera.position.z) + Math.sin(camera.rotation.y) * (Math.sin(camera.rotation.z) * (point3d.y - camera.position.y) +
				Math.cos(camera.rotation.z) * (point3d.x - camera.position.x))) + Math.cos(camera.rotation.x) * (Math.cos(camera.rotation.z) * (point3d.y - camera.position.y) - Math.sin(camera.rotation.z) * (point3d.x - camera.position.x));
		
		screenPoint.z = Math.cos(camera.rotation.x) * (Math.cos(camera.rotation.y) * (point3d.z - camera.position.z) + Math.sin(camera.rotation.y) * (Math.sin(camera.rotation.z) * (point3d.y - camera.position.y) +
				Math.cos(camera.rotation.z) * (point3d.x - camera.position.x))) - Math.sin(camera.rotation.x) * (Math.cos(camera.rotation.z) * (point3d.y - camera.position.y) - Math.sin(camera.rotation.z) * (point3d.x - camera.position.x));
		
		Vector3 newPoint = new Vector3();
		
		double ez = 1 / Math.tan(camera.getFov() / 2);
		double x = (screenPoint.x - .5) * (ez / screenPoint.z);
		double y = -(screenPoint.y - .5) * (ez / screenPoint.z);
		
		newPoint.x = (int) (x * frame.getFrame().getWidth()) + frame.getFrame().getWidth() / 2;
		newPoint.y = (int) (y * frame.getFrame().getHeight()) + frame.getFrame().getHeight() / 2;
		newPoint.z = screenPoint.z;
		
		return newPoint;
	}
}