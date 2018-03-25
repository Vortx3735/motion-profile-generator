package com.jtulayan.ui.javafx;

import com.jtulayan.main.ProfileGenerator;
import com.jtulayan.main.PropWrapper;
import jaci.pathfinder.Pathfinder;
import jaci.pathfinder.Trajectory;
import jaci.pathfinder.Waypoint;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableValueBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import javafx.util.converter.DoubleStringConverter;
import javafx.scene.chart.Axis;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.tools.Tool;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.List;

public class MPGenController {
    private ProfileGenerator backend;

    @FXML
    private Pane root;

    @FXML
    private TextField
            txtTimeStep,
            txtVelocity,
            txtAcceleration,
            txtJerk,
            txtWheelBaseW,
            txtWheelBaseD;

    @FXML
    private Label
        lblWheelBaseD;

    @FXML
    private TableView<Waypoint> tblWaypoints;

    @FXML
    private LineChart<Double, Double>
            chtPosition,
            chtVelocity;

    @FXML
    private NumberAxis
        axisPosX,
        axisPosY,
        axisTime,
        axisVel;

    @FXML
    private TableColumn<Waypoint, Double>
            colWaypointX,
            colWaypointY,
            colWaypointAngle;

    @FXML
    private MenuItem
            mnuOpen,
            mnuFileNew,
            mnuFileSave,
            mnuFileSaveAs,
            mnuFileExport,
            mnuFileExit,
            mnuHelpAbout;

    @FXML
    private ChoiceBox
            choDriveBase,
            choFitMethod,
            choUnits;

    @FXML
    private Button
            btnAddPoint,
            btnClearPoints,
            btnDelete;

    @FXML
    private ImageView
            imgOverlay;

    private ObservableList<Waypoint> waypointsList;

    private Properties properties;

    @FXML
    public void initialize() {
        backend = new ProfileGenerator();
        properties = PropWrapper.getProperties();

        chtPosition.onMouseClickedProperty().set(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent mev)
            {
                Axis<Double> xAxis = chtPosition.getXAxis();
                Axis<Double> yAxis = chtPosition.getYAxis();

              
                Double xScaler = xAxis.getWidth() / xAxis.getTickMarks().size();
                Double yScaler = yAxis.getHeight() / yAxis.getTickMarks().size();

                waypointsList.add(new Waypoint((mev.getX() - xAxis.getZeroPosition()) / xScaler, (yAxis.getZeroPosition() - mev.getY()) / yScaler, 0));
                
            }
        });


        btnDelete.setDisable(true);

        choDriveBase.setItems(FXCollections.observableArrayList("Tank", "Swerve"));
        choDriveBase.setValue(choDriveBase.getItems().get(0));
        choDriveBase.getSelectionModel().selectedItemProperty().addListener(this::updateDriveBase);

        choFitMethod.setItems(FXCollections.observableArrayList("Cubic", "Quintic"));
        choFitMethod.setValue(choFitMethod.getItems().get(0));
        choFitMethod.getSelectionModel().selectedItemProperty().addListener(this::updateFitMethod);

        choUnits.setItems(FXCollections.observableArrayList("Imperial", "Metric"));
        choUnits.setValue(choUnits.getItems().get(0));
        choUnits.getSelectionModel().selectedItemProperty().addListener(this::updateUnits);

        Callback<TableColumn<Waypoint, Double>, TableCell<Waypoint, Double>> doubleCallback =
            (TableColumn<Waypoint, Double> param) -> {
                TextFieldTableCell<Waypoint, Double> cell = new TextFieldTableCell<>();

                cell.setConverter(new DoubleStringConverter());

                return cell;
        };

        EventHandler<TableColumn.CellEditEvent<Waypoint, Double>> editHandler =
            (TableColumn.CellEditEvent<Waypoint, Double> t) -> {
                Waypoint curWaypoint = t.getRowValue();

                if (t.getTableColumn() == colWaypointAngle)
                    curWaypoint.angle = Pathfinder.d2r(t.getNewValue());
                else if (t.getTableColumn() == colWaypointY)
                    curWaypoint.y = t.getNewValue();
                else
                    curWaypoint.x = t.getNewValue();

                generateTrajectories();
        };

        txtTimeStep.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtVelocity.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtAcceleration.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtJerk.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtWheelBaseW.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtWheelBaseD.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));

        txtTimeStep.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtTimeStep.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "0.02";
                    txtTimeStep.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtTimeStep.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtVelocity.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtVelocity.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "4.0";
                    txtVelocity.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtVelocity.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtAcceleration.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtAcceleration.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "3.0";
                    txtAcceleration.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtAcceleration.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtJerk.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtJerk.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "60.0";
                    txtJerk.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtJerk.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtWheelBaseW.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtWheelBaseW.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "1.464";
                    txtWheelBaseW.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtWheelBaseW.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtWheelBaseD.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtWheelBaseD.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "1.464";
                    txtWheelBaseD.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtWheelBaseD.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        colWaypointX.setCellFactory(doubleCallback);
        colWaypointY.setCellFactory(doubleCallback);
        colWaypointAngle.setCellFactory(doubleCallback);

        colWaypointX.setOnEditCommit(editHandler);
        colWaypointY.setOnEditCommit(editHandler);
        colWaypointAngle.setOnEditCommit(editHandler);

        colWaypointX.setCellValueFactory((TableColumn.CellDataFeatures<Waypoint, Double> d) ->
            new ObservableValueBase<Double>() {
                @Override
                public Double getValue() {
                    return d.getValue().x;
                }
            }
        );

        colWaypointY.setCellValueFactory((TableColumn.CellDataFeatures<Waypoint, Double> d) ->
            new ObservableValueBase<Double>() {
                @Override
                public Double getValue() {
                    return d.getValue().y;
                }
           }
        );

        colWaypointAngle.setCellValueFactory((TableColumn.CellDataFeatures<Waypoint, Double> d) ->
            new ObservableValueBase<Double>() {
                @Override
                public Double getValue() {
                    return Pathfinder.r2d(d.getValue().angle);
                }
            }
        );

        waypointsList = FXCollections.observableList(backend.getWaypointsList());
        waypointsList.addListener((ListChangeListener<Waypoint>) c -> {
            btnClearPoints.setDisable(waypointsList.size() == 0);
            generateTrajectories();
        });

        tblWaypoints.setItems(waypointsList);
        tblWaypoints.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tblWaypoints.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) ->
                btnDelete.setDisable(tblWaypoints.getSelectionModel().getSelectedIndices().get(0) == -1)
        );

        updateOverlayImg();
        updateFrontend();
    }

    @FXML
    private void showSettingsDialog() {
        Dialog<Boolean> settingsDialog = new Dialog<>();
        Optional<Boolean> result = null;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("SettingsDialog.fxml"));
            settingsDialog.setDialogPane(loader.load());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Some header stuff
        settingsDialog.setTitle("Settings");
        settingsDialog.setHeaderText("Manage settings");

        settingsDialog.setResultConverter((ButtonType buttonType) ->
                buttonType.getButtonData() == ButtonBar.ButtonData.APPLY
        );

        // Wait for the result
        result = settingsDialog.showAndWait();

        result.ifPresent((Boolean b) -> {
            if (b) {
                try {
                    DialogPane pane = settingsDialog.getDialogPane();

                    String overlayDir = ((TextField) pane.lookup("#txtOverlayDir")).getText().trim();

                    properties.setProperty("ui.overlayDir", overlayDir);

                    updateOverlayImg();
                    PropWrapper.storeProperties();
                } catch (IOException e) {
                    Alert alert = AlertFactory.createExceptionAlert(e);

                    alert.showAndWait();
                }
            }
        });
    }

    @FXML
    private void deletePoints() {
        List<Integer> selectedIndicies = tblWaypoints.getSelectionModel().getSelectedIndices();

        int firstIndex = selectedIndicies.get(0);
        int lastIndex = selectedIndicies.get(selectedIndicies.size() - 1);

        waypointsList.remove(firstIndex, lastIndex + 1);
    }

    @FXML
    private void showSaveAsDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setTitle("Save As");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Extensive Markup Language", "*.xml")
        );

        File result = fileChooser.showSaveDialog(root.getScene().getWindow());

        if (result != null)
            try {
                backend.saveProjectAs(result);

                mnuFileSave.setDisable(false);
            } catch (Exception e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
        }
    }

    @FXML
    private void showOpenDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setTitle("Open Project");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Extensive Markup Language", "*.xml")
        );

        File result = fileChooser.showOpenDialog(root.getScene().getWindow());

        if (result != null) {
            try {
                backend.loadProject(result);

                updateFrontend();
                updateChartAxes();

                generateTrajectories();

                mnuFileSave.setDisable(false);
            } catch (Exception e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
            }
        }
    }

    @FXML
    private void save() {
        updateBackend();

        try {
            backend.saveWorkingProject();
        } catch (Exception e) {
            Alert alert = AlertFactory.createExceptionAlert(e);

            alert.showAndWait();
        }
    }

    @FXML
    private void showExportDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setTitle("Export");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Comma Separated Values", "*.csv"),
                new FileChooser.ExtensionFilter("Binary Trajectory File", "*.traj")
        );

        File result = fileChooser.showSaveDialog(root.getScene().getWindow());

        if (result != null && generateTrajectories()) {
            String parentPath = result.getAbsolutePath(), ext = parentPath.substring(parentPath.lastIndexOf("."));
            parentPath = parentPath.substring(0, parentPath.lastIndexOf(ext));

            try {
                backend.exportTrajectories(new File(parentPath), ext);
            } catch (Pathfinder.GenerationException e) {
                Alert alert = AlertFactory.createExceptionAlert(e, "Invalid Trajectory!");

                alert.showAndWait();
            }
        }
    }

    @FXML
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setTitle("Import");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vannaka Properties File", "*.bot")
        );

        File result = fileChooser.showOpenDialog(root.getScene().getWindow());

        if (result != null) {
            Dialog<ProfileGenerator.Units> unitsSelector = new Dialog<>();
            ButtonType okButtonType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
            Optional<ProfileGenerator.Units> unitsResult = null;
            GridPane grid = new GridPane();
            ToggleGroup radGroup = new ToggleGroup();
            RadioButton
                radImperial = new RadioButton("Imperial (ft)"),
                radMetric = new RadioButton("Metric (m)");

            // Some header stuff
            unitsSelector.setTitle("Select Units");
            unitsSelector.setHeaderText("Select the distance units being used");

            // Some other UI stuff
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            grid.add(radImperial, 0, 0);
            grid.add(radMetric, 0, 1);

            radImperial.setToggleGroup(radGroup);
            radImperial.selectedProperty().set(true);
            radMetric.setToggleGroup(radGroup);

            unitsSelector.getDialogPane().setContent(grid);

            // Add all buttons
            unitsSelector.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
            unitsSelector.setResultConverter(buttonType -> {
                if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    if (radMetric.selectedProperty().getValue())
                        return ProfileGenerator.Units.METRIC;
                    else
                        return ProfileGenerator.Units.IMPERIAL;
                }

                return null;
            });

            unitsResult = unitsSelector.showAndWait();

            unitsResult.ifPresent(u -> {
                backend.clearPoints();
                try {
                    backend.importBotFile(result, u);

                    updateFrontend();
                    generateTrajectories();
                } catch (Exception e) {
                    Alert alert = AlertFactory.createExceptionAlert(e);

                    alert.showAndWait();
                }
            });
        }
    }

    @FXML
    private void showAddPointDialog() {
        Dialog<Waypoint> waypointDialog = new Dialog<>();
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        Optional<Waypoint> result = null;
        GridPane grid = new GridPane();
        TextField
                txtWX = new TextField(),
                txtWY = new TextField(),
                txtWA = new TextField();

        // Some header stuff
        waypointDialog.setTitle("Add Point");
        waypointDialog.setHeaderText("Add a new waypoint");

        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("X:"), 0, 0);
        grid.add(txtWX, 1, 0);
        grid.add(new Label("Y:"), 0, 1);
        grid.add(txtWY, 1, 1);
        grid.add(new Label("Angle:"), 0, 2);
        grid.add(txtWA, 1, 2);

        waypointDialog.getDialogPane().setContent(grid);

        // Add all buttons
        waypointDialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        waypointDialog.getDialogPane().lookupButton(addButtonType).addEventFilter(ActionEvent.ACTION, ae -> {
            try {
                Double.parseDouble(txtWX.getText().trim());
                Double.parseDouble(txtWY.getText().trim());
                Double.parseDouble(txtWA.getText().trim());
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.WARNING);

                alert.setTitle("Invalid Point!");
                alert.setHeaderText("Invalid point input!");
                alert.setContentText("Please check your fields and try again.");

                Toolkit.getDefaultToolkit().beep();
                alert.showAndWait();
                ae.consume();
            }
        });


        waypointDialog.setResultConverter((ButtonType buttonType) -> {
            if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                double
                        x = Double.parseDouble(txtWX.getText().trim()),
                        y = Double.parseDouble(txtWY.getText().trim()),
                        angle = Double.parseDouble(txtWA.getText().trim());

                return new Waypoint(x, y, Pathfinder.d2r(angle));
            }

            return null;
        });

        // Wait for the result
        result = waypointDialog.showAndWait();

        result.ifPresent((Waypoint w) -> waypointsList.add(w));
    }

    @FXML
    private void showClearPointsDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);

        alert.setTitle("Clear Points");
        alert.setHeaderText("Clear All Points?");
        alert.setContentText("Are you sure you want to clear all points?");

        Optional<ButtonType> result = alert.showAndWait();

        result.ifPresent((ButtonType t) -> {
            if (t.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                waypointsList.clear();
        });
    }

    @FXML
    private void resetData() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);

        alert.setTitle("Create New Project?");
        alert.setHeaderText("Confirm Reset");
        alert.setContentText("Are you sure you want to reset all data? Have you saved?");

        Optional<ButtonType> result = alert.showAndWait();

        result.ifPresent((ButtonType t) -> {
            if (t == ButtonType.OK) {
                backend.clearWorkingFiles();
                backend.resetValues();
                waypointsList.clear();

                updateFrontend();
                updateChartAxes();

                mnuFileSave.setDisable(true);
            }
        });
    }

    @FXML
    private void validateFieldEdit(ActionEvent event) {
        String val = ((TextField) event.getSource()).getText().trim();
        double d = 0;
        boolean validInput = true;

        try {
            d = Double.parseDouble(val);

            validInput = d > 0;
        } catch (NumberFormatException e) {
            validInput = false;
        } finally {
            if (validInput)
                generateTrajectories();
            else
                Toolkit.getDefaultToolkit().beep();
        }
    }

    @FXML
    private void updateBackend() {
        backend.setTimeStep(Double.parseDouble(txtTimeStep.getText().trim()));
        backend.setVelocity(Double.parseDouble(txtVelocity.getText().trim()));
        backend.setAcceleration(Double.parseDouble(txtAcceleration.getText().trim()));
        backend.setJerk(Double.parseDouble(txtJerk.getText().trim()));
        backend.setWheelBaseW(Double.parseDouble(txtWheelBaseW.getText().trim()));
        backend.setWheelBaseD(Double.parseDouble(txtWheelBaseD.getText().trim()));
    }

    /**
     * Updates all fields and views in the UI.
     */
    private void updateFrontend() {
        txtTimeStep.setText("" + backend.getTimeStep());
        txtVelocity.setText("" + backend.getVelocity());
        txtAcceleration.setText("" + backend.getAcceleration());
        txtJerk.setText("" + backend.getJerk());
        txtWheelBaseW.setText("" + backend.getWheelBaseW());
        txtWheelBaseD.setText("" + backend.getWheelBaseD());

        choDriveBase.setValue(choDriveBase.getItems().get(backend.getDriveBase().ordinal()));
        choFitMethod.setValue(choFitMethod.getItems().get(backend.getFitMethod().ordinal()));
        choUnits.setValue(choUnits.getItems().get(backend.getUnits().ordinal()));

        refreshWaypointTable();
    }

    @FXML
    private void openGithubPage() {
        try {
            Desktop.getDesktop().browse(new URI("https://github.com/Endoman123/motion-profile-generator"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void exit() {
        System.exit(0);
    }

    private boolean generateTrajectories() {
        updateBackend();

        if (waypointsList.size() > 1) {
            try {
                backend.updateTrajectories();
            } catch (Pathfinder.GenerationException e) {
                Toolkit.getDefaultToolkit().beep();

                Alert alert = new Alert(Alert.AlertType.WARNING);

                alert.setTitle("Invalid Trajectory");
                alert.setHeaderText("Invalid trajectory point!");
                alert.setContentText("The trajectory point is invalid because one of the waypoints is invalid! " +
                        "Please check the waypoints and try again.");
                alert.showAndWait();
            }
        }
        else
        {
            chtPosition.getData().clear();
            chtVelocity.getData().clear();
        }

        repopulatePosChart();
        repopulateVelChart();

        return true;
    }

    private void updateDriveBase(ObservableValue<String> observable, Object oldValue, Object newValue) {
        String choice = ((String) newValue).toUpperCase();
        ProfileGenerator.DriveBase db = ProfileGenerator.DriveBase.valueOf(choice);

        backend.setDriveBase(db);

        txtWheelBaseD.setDisable(db == ProfileGenerator.DriveBase.TANK);
        lblWheelBaseD.setDisable(db == ProfileGenerator.DriveBase.TANK);

        generateTrajectories();
    }

    private void updateFitMethod(ObservableValue<String> observable, Object oldValue, Object newValue) {
        String choice = ((String) newValue).toUpperCase();
        Trajectory.FitMethod fm = Trajectory.FitMethod.valueOf("HERMITE_" + choice);

        backend.setFitMethod(fm);

        generateTrajectories();
    }

    private void updateUnits(ObservableValue<String> observable, Object oldValue, Object newValue) {
        String choice = ((String) newValue).toUpperCase();
        ProfileGenerator.Units u = ProfileGenerator.Units.valueOf(choice);

        backend.setUnits(u);
        updateChartAxes();
    }

    private void repopulatePosChart() {
        // Clear data from position graph
        chtPosition.getData().clear();

        if (waypointsList.size() > 1) {
            SegmentSeries
                    fl = new SegmentSeries(backend.getFrontLeftTrajectory()),
                    fr = new SegmentSeries(backend.getFrontRightTrajectory());

            XYChart.Series<Double, Double>
                    flSeries = fl.getPositionSeries(),
                    frSeries = fr.getPositionSeries();

            if (backend.getDriveBase() == ProfileGenerator.DriveBase.SWERVE) {
                SegmentSeries
                        bl = new SegmentSeries(backend.getBackLeftTrajectory()),
                        br = new SegmentSeries(backend.getBackRightTrajectory());

                XYChart.Series<Double, Double>
                        blSeries = bl.getPositionSeries(),
                        brSeries = br.getPositionSeries();

                chtPosition.getData().addAll(blSeries, brSeries, flSeries, frSeries);
                flSeries.getNode().setStyle("-fx-stroke: red");
                frSeries.getNode().setStyle("-fx-stroke: red");
                blSeries.getNode().setStyle("-fx-stroke: blue");
                brSeries.getNode().setStyle("-fx-stroke: blue");
            } else {
                chtPosition.getData().addAll(flSeries, frSeries);

                flSeries.getNode().setStyle("-fx-stroke: magenta");
                frSeries.getNode().setStyle("-fx-stroke: magenta");
            }
        }
    }

    private void repopulateVelChart() {
        // Clear data from velocity graph
        chtVelocity.getData().clear();

        if (waypointsList.size() > 1) {
            SegmentSeries
                    fl = new SegmentSeries(backend.getFrontLeftTrajectory()),
                    fr = new SegmentSeries(backend.getFrontRightTrajectory());

            XYChart.Series<Double, Double>
                    flSeries = fl.getVelocitySeries(),
                    frSeries = fr.getVelocitySeries();

            chtVelocity.getData().addAll(flSeries, frSeries);

            if (backend.getDriveBase() == ProfileGenerator.DriveBase.SWERVE) {
                SegmentSeries
                        bl = new SegmentSeries(backend.getBackLeftTrajectory()),
                        br = new SegmentSeries(backend.getBackRightTrajectory());

                XYChart.Series<Double, Double>
                        blSeries = bl.getVelocitySeries(),
                        brSeries = br.getVelocitySeries();

                chtVelocity.getData().addAll(blSeries, brSeries);

                flSeries.setName("Front Left Trajectory");
                frSeries.setName("Front Right Trajectory");
                blSeries.setName("Back Left Trajectory");
                brSeries.setName("Back Right Trajectory");
            } else {
                flSeries.setName("Left Trajectory");
                frSeries.setName("Right Trajectory");
            }
        }
    }

    private void updateOverlayImg() {
        String dir = properties.getProperty("ui.overlayDir", "");

        if (!dir.isEmpty()) {
            try {
                imgOverlay.setImage(new Image(new FileInputStream(dir)));

                // Wire up the clicks on the image to click through to the chart
                imgOverlay.setOnMouseClicked(chtPosition.getOnMouseClicked());
            } catch (FileNotFoundException e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
            }
        }
    }

    private void updateChartAxes() {
        switch (backend.getUnits()) {
            case IMPERIAL:
                axisPosX.setUpperBound(32);
                axisPosX.setTickUnit(1);
                axisPosX.setLabel("X-Position (ft)");
                axisPosY.setUpperBound(27);
                axisPosY.setTickUnit(1);
                axisPosY.setLabel("Y-Position (ft)");

                axisVel.setLabel("Velocity (ft/s)");

                break;
            case METRIC:
                axisPosX.setUpperBound(10);
                axisPosX.setTickUnit(0.5);
                axisPosX.setLabel("X-Position (m)");
                axisPosY.setUpperBound(8.23);
                axisPosY.setTickUnit(0.5);
                axisPosY.setLabel("Y-Position (m)");

                axisVel.setLabel("Velocity (m/s)");

                break;
            default:
                backend.setUnits(ProfileGenerator.Units.IMPERIAL);
                updateChartAxes();
        }
    }

    /**
     * Refreshes the waypoints table by clearing the waypoint list and repopulating it.
     */
    public void refreshWaypointTable() {
        // Bad way to update the waypoint list...
        // However, TableView.refresh() is apparently borked?
        List<Waypoint> tmp = new ArrayList<>(backend.getWaypointsList());
        waypointsList.clear();
        waypointsList.addAll(tmp);
    }
}
