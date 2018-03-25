package com.jtulayan.main;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import jaci.pathfinder.Pathfinder;
import jaci.pathfinder.Trajectory;
import jaci.pathfinder.Trajectory.Config;
import jaci.pathfinder.Trajectory.FitMethod;
import jaci.pathfinder.Waypoint;
import jaci.pathfinder.modifiers.SwerveModifier;
import jaci.pathfinder.modifiers.TankModifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The "backend" of the motion profile generator.
 * Mainly to interface with the entire system.
 * Also handles saving, loading, etc.
 */
public class ProfileGenerator {
    public static final String PROJECT_EXTENSION = "xml";

    public enum DriveBase {
        TANK,
        SWERVE
    }

    public enum Units {
        IMPERIAL,
        METRIC
    }

    private double timeStep;
    private double velocity;
    private double acceleration;
    private double jerk;
    private double wheelBaseW;
    private double wheelBaseD;

    private DriveBase driveBase;
    private FitMethod fitMethod;
    private Units units;

    private final List<Waypoint> POINTS;

    // Trajectories for both bases
    // Use front-left and front-right for tank drive L and R
    private Trajectory fl;
    private Trajectory fr;
    private Trajectory bl;
    private Trajectory br;

    // Source trajectory
    // i.e. the center trajectory
    private Trajectory source;

    // File stuff
    private DocumentBuilderFactory dbFactory;
    private File workingProject;

    public ProfileGenerator() {
        POINTS = new ArrayList<>();
        dbFactory = DocumentBuilderFactory.newInstance();
        resetValues();
    }

    /**
     * Saves the project in XML format.
     *
     * @param path the absolute file path to save to, including file name and extension
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public void saveProjectAs(File path) throws IOException, ParserConfigurationException {
        if (!path.getAbsolutePath().endsWith("." + PROJECT_EXTENSION))
            path = new File(path + "." + PROJECT_EXTENSION);

        File dir = path.getParentFile();

        if (dir != null && !dir.exists() && dir.isDirectory()) {
            if (!dir.mkdirs())
                return;
        }

        if (path.exists() && !path.delete())
            return;

        workingProject = path;

        saveWorkingProject();
    }

    /**
     * Saves the working project.
     *
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public void saveWorkingProject() throws IOException, ParserConfigurationException {
        if (workingProject != null) {
            // Create document
            DocumentBuilder db = dbFactory.newDocumentBuilder();
            Document dom = db.newDocument();

            Element trajectoryEle = dom.createElement("Trajectory");

            trajectoryEle.setAttribute("dt", "" + timeStep);
            trajectoryEle.setAttribute("velocity", "" + velocity);
            trajectoryEle.setAttribute("acceleration", "" + acceleration);
            trajectoryEle.setAttribute("jerk", "" + jerk);
            trajectoryEle.setAttribute("wheelBaseW", "" + wheelBaseW);
            trajectoryEle.setAttribute("wheelBaseD", "" + wheelBaseD);
            trajectoryEle.setAttribute("fitMethod", "" + fitMethod.toString());
            trajectoryEle.setAttribute("driveBase", "" + driveBase.toString());
            trajectoryEle.setAttribute("units", "" + units.toString());

            dom.appendChild(trajectoryEle);

            for (Waypoint w : POINTS) {
                Element waypointEle = dom.createElement("Waypoint");
                Element xEle = dom.createElement("X");
                Element yEle = dom.createElement("Y");
                Element angleEle = dom.createElement("Angle");
                Text xText = dom.createTextNode("" + w.x);
                Text yText = dom.createTextNode("" + w.y);
                Text angleText = dom.createTextNode("" + w.angle);

                xEle.appendChild(xText);
                yEle.appendChild(yText);
                angleEle.appendChild(angleText);

                waypointEle.appendChild(xEle);
                waypointEle.appendChild(yEle);
                waypointEle.appendChild(angleEle);

                trajectoryEle.appendChild(waypointEle);
            }

            OutputFormat format = new OutputFormat(dom);

            format.setIndenting(true);

            XMLSerializer xmlSerializer = new XMLSerializer(
                    new FileOutputStream(workingProject), format
            );

            xmlSerializer.serialize(dom);
        }
    }

    /**
     * Exports all trajectories to the parent folder, with the given root name and file extension.
     *
     * @param parentPath the absolute file path to save to, excluding file extension
     * @param ext        the file extension to save to, can be {@code *.csv} or {@code *.traj}
     * @throws Pathfinder.GenerationException
     */
    public void exportTrajectories(File parentPath, String ext) throws Pathfinder.GenerationException {
        updateTrajectories();

        File dir = parentPath.getParentFile();

        if (dir != null && !dir.exists() && dir.isDirectory()) {
            if (!dir.mkdirs())
                return;
        }

        switch (ext) {
            case ".csv":
                Pathfinder.writeToCSV(new File(parentPath + "_source_detailed.csv"), source);

                if (driveBase == DriveBase.SWERVE) {
                    Pathfinder.writeToCSV(new File(parentPath + "_fl_detailed.csv"), fl);
                    Pathfinder.writeToCSV(new File(parentPath + "_fr_detailed.csv"), fr);
                    Pathfinder.writeToCSV(new File(parentPath + "_bl_detailed.csv"), bl);
                    Pathfinder.writeToCSV(new File(parentPath + "_br_detailed.csv"), br);
                } else {
                    Pathfinder.writeToCSV(new File(parentPath + "_left_detailed.csv"), fl);
                    Pathfinder.writeToCSV(new File(parentPath + "_right_detailed.csv"), fr);
                }
            break;
            case ".traj":
                Pathfinder.writeToFile(new File(parentPath + "_source_detailed.traj"), source);

                if (driveBase == DriveBase.SWERVE) {
                    Pathfinder.writeToFile(new File(parentPath + "_fl_detailed.traj"), fl);
                    Pathfinder.writeToFile(new File(parentPath + "_fr_detailed.traj"), fr);
                    Pathfinder.writeToFile(new File(parentPath + "_bl_detailed.traj"), bl);
                    Pathfinder.writeToFile(new File(parentPath + "_br_detailed.traj"), br);
                } else {
                    Pathfinder.writeToFile(new File(parentPath + "_left_detailed.traj"), fl);
                    Pathfinder.writeToFile(new File(parentPath + "_right_detailed.traj"), fr);
                }
            break;
            default:
                throw new IllegalArgumentException("Invalid file extension");
        }
    }

    /**
     * Loads a project from file.
     *
     * @param path the absolute file path to load the project from
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public void loadProject(File path) throws IOException, ParserConfigurationException, SAXException {
        if (!path.exists() || path.isDirectory())
            return;

        if (path.getAbsolutePath().toLowerCase().endsWith("." + PROJECT_EXTENSION)) {
            DocumentBuilder db = dbFactory.newDocumentBuilder();

            Document dom = db.parse(path);

            Element docEle = dom.getDocumentElement();

            timeStep = Double.parseDouble(docEle.getAttribute("dt"));
            velocity = Double.parseDouble(docEle.getAttribute("velocity"));
            acceleration = Double.parseDouble(docEle.getAttribute("acceleration"));
            jerk = Double.parseDouble(docEle.getAttribute("jerk"));
            wheelBaseW = Double.parseDouble(docEle.getAttribute("wheelBaseW"));
            wheelBaseD = Double.parseDouble(docEle.getAttribute("wheelBaseD"));

            driveBase = DriveBase.valueOf(docEle.getAttribute("driveBase"));
            fitMethod = FitMethod.valueOf(docEle.getAttribute("fitMethod"));
            units = Units.valueOf(docEle.getAttribute("units"));

            NodeList waypointEleList = docEle.getElementsByTagName("Waypoint");

            POINTS.clear();
            if (waypointEleList != null && waypointEleList.getLength() > 0) {
                for (int i = 0; i < waypointEleList.getLength(); i++) {
                    Element waypointEle = (Element) waypointEleList.item(i);

                    String
                            xText = waypointEle.getElementsByTagName("X").item(0).getTextContent(),
                            yText = waypointEle.getElementsByTagName("Y").item(0).getTextContent(),
                            angleText = waypointEle.getElementsByTagName("Angle").item(0).getTextContent();

                    POINTS.add(new Waypoint(
                            Double.parseDouble(xText),
                            Double.parseDouble(yText),
                            Double.parseDouble(angleText)
                    ));
                }
            }

            workingProject = path;
        }
    }

    /**
     * Imports a Vannaka properties (*.bot) file into the generator.
     * This import method should work with vannaka properties files generated from version 2.3.0.
     *
     * @param path     the file path of the bot file
     * @param botUnits the units to use for this bot file
     * @throws IOException
     */
    public void importBotFile(File path, Units botUnits) throws IOException, NumberFormatException {
        if (!path.exists() || path.isDirectory())
            return;

        if (path.getAbsolutePath().toLowerCase().endsWith(".bot")) {
            BufferedReader botReader = new BufferedReader(new FileReader(path));
            Stream<String> botStream = botReader.lines();
            List<String> botLines = botStream.collect(Collectors.toList());

            // First off we need to set the units of distance being used in the file.
            // Unfortunately it is not explicitly saved to file; we will need some user input on that.
            units = botUnits;

            // Now we can read the first 7 lines and assign them accordingly.
            timeStep = Math.abs(Double.parseDouble(botLines.get(0).trim()));
            velocity = Math.abs(Double.parseDouble(botLines.get(1).trim()));
            acceleration = Math.abs(Double.parseDouble(botLines.get(2).trim()));
            jerk = Math.abs(Double.parseDouble(botLines.get(3).trim()));
            wheelBaseW = Math.abs(Double.parseDouble(botLines.get(4).trim()));
            wheelBaseD = Math.abs(Double.parseDouble(botLines.get(5).trim()));

            fitMethod = FitMethod.valueOf("HERMITE_" + botLines.get(6).trim().toUpperCase());

            if (wheelBaseD > 0) // Assume that the wheel base was swerve
                driveBase = DriveBase.SWERVE;

            // GLHF parse the rest of the file I guess...
            for (int i = 7; i < botLines.size(); i++) {
                String[] waypointVals = botLines.get(i).split(",");

                POINTS.add(new Waypoint(
                    Double.parseDouble(waypointVals[0].trim()),
                    Double.parseDouble(waypointVals[1].trim()),
                    Math.toRadians(Double.parseDouble(waypointVals[2].trim()))
                ));
            }
        }
    }

    /**
     * Adds a waypoint to the list of waypoints
     *
     * @param x     the x-location of the waypoint
     * @param y     the y-location of the waypoint
     * @param angle the angle of direction at the point, in radians
     */
    public void addPoint(double x, double y, double angle) {
        POINTS.add(new Waypoint(x, y, angle));
    }

    /**
     * Adds a waypoint to the list of waypoints
     *
     * @param index the index of the waypoint to edit
     * @param x     the x-location of the waypoint
     * @param y     the y-location of the waypoint
     * @param angle the angle of direction at the point, in radians
     */
    public void editWaypoint(int index, double x, double y, double angle) {
        POINTS.get(index).x = x;
        POINTS.get(index).y = y;
        POINTS.get(index).angle = angle;
    }

    public void removePoint(int index) {
        POINTS.remove(index);
    }

    public int getWaypointsSize() {
        return POINTS.size();
    }

    /**
     * Resets configuration to default values
     */
    public void resetValues() {
        timeStep = 0.05;
        velocity = 4;
        acceleration = 3;
        jerk = 60;
        wheelBaseW = 1.464;
        wheelBaseD = 0;

        fitMethod = FitMethod.HERMITE_CUBIC;
        driveBase = DriveBase.TANK;
        units = Units.IMPERIAL;
    }

    /**
     * Clears all the existing waypoints in the list.
     * This also clears all trajectories generated by the waypoints.
     */
    public void clearPoints() {
        POINTS.clear();

        fl = null;
        fr = null;
        bl = null;
        br = null;
    }

    /**
     * Clears the working project files
     */
    public void clearWorkingFiles() {
        workingProject = null;
    }

    /**
     * Updates the trajectories
     */
    public void updateTrajectories() throws Pathfinder.GenerationException {
        Config config = new Config(fitMethod, Config.SAMPLES_HIGH, timeStep, velocity, acceleration, jerk);
        source = Pathfinder.generate(POINTS.toArray(new Waypoint[1]), config);

        if (driveBase == DriveBase.SWERVE) {
            SwerveModifier swerve = new SwerveModifier(source);

            // There is literally no other swerve mode other than the default can someone please explain this to me
            swerve.modify(wheelBaseW, wheelBaseD, SwerveModifier.Mode.SWERVE_DEFAULT);

            fl = swerve.getFrontLeftTrajectory();
            fr = swerve.getFrontRightTrajectory();
            bl = swerve.getBackLeftTrajectory();
            br = swerve.getBackRightTrajectory();
        } else { // By default, treat everything as tank drive.
            TankModifier tank = new TankModifier(source);
            tank.modify(wheelBaseW);

            fl = tank.getLeftTrajectory();
            fr = tank.getRightTrajectory();
            bl = null;
            br = null;
        }
    }

    public double getTimeStep() {
        return timeStep;
    }

    public void setTimeStep(double timeStep) {
        this.timeStep = timeStep;
    }

    public double getVelocity() {
        return velocity;
    }

    public void setVelocity(double velocity) {
        this.velocity = velocity;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(double acceleration) {
        this.acceleration = acceleration;
    }

    public DriveBase getDriveBase() {
        return driveBase;
    }

    public void setDriveBase(DriveBase driveBase) {
        this.driveBase = driveBase;
    }

    public FitMethod getFitMethod() {
        return fitMethod;
    }

    public void setFitMethod(FitMethod fitMethod) {
        this.fitMethod = fitMethod;
    }

    public Units getUnits() {
        return units;
    }

    public void setUnits(Units units) {
        this.units = units;
    }

    public double getJerk() {
        return jerk;
    }

    public void setJerk(double jerk) {
        this.jerk = jerk;
    }

    public double getWheelBaseW() {
        return wheelBaseW;
    }

    public void setWheelBaseW(double wheelBaseW) {
        this.wheelBaseW = wheelBaseW;
    }

    public double getWheelBaseD() {
        return wheelBaseD;
    }

    public void setWheelBaseD(double wheelBaseD) {
        this.wheelBaseD = wheelBaseD;
    }

    public boolean hasWorkingProject() {
        return workingProject != null;
    }

    public List<Waypoint> getWaypointsList() {
        return POINTS;
    }

    public Trajectory getSourceTrajectory() {
        return source;
    }

    public Trajectory getFrontLeftTrajectory() {
        return fl;
    }

    public Trajectory getFrontRightTrajectory() {
        return fr;
    }

    public Trajectory getBackLeftTrajectory() {
        return bl;
    }

    public Trajectory getBackRightTrajectory() {
        return br;
    }

}
