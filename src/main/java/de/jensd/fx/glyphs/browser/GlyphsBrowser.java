/**
 * Copyright (c) 2016 Jens Deters http://www.jensd.de
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package de.jensd.fx.glyphs.browser;

import de.jensd.fx.glyphs.GlyphIcon;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jens Deters
 */
public class GlyphsBrowser extends VBox {

    @FXML
    private Label numberOfIconsLabel;
    @FXML
    private Slider glyphSizeSlider;
    @FXML
    private Label glyphSizeSliderValueLabel;
    @FXML
    private Label fontNameLabel;
    @FXML
    private Label fontFamilyLabel;
    @FXML
    private Label fontVersionLabel;
    @FXML
    private Label fontLicenseLabel;
    @FXML
    private Label fontReleaseDateLabel;
    @FXML
    private Hyperlink fontUrlLabel;
    @FXML
    private Label fontWhatsNewLabel;
    @FXML
    private TextField glyphNameLabel;
    @FXML
    private TextField glyphCodeLabel;
    @FXML
    private TextField glyphFactoryCodeLabel;
    @FXML
    private Button copyCodeButton;
    @FXML
    private Button copyFactoryCodeButton;
    @FXML
    private ListView<GlyphsPack> glyphsPackListView;
    @FXML
    private TableView<List<GlyphIcon>> glyphsTableView;
    @FXML
    private Pane glyphPreviewPane;
    @FXML
    private TextField searchBar;
    @FXML
    private Label searchBarResultsLabel;

    private final GlyphsBrowserAppModel model;

    private int MAX_COLS = 4;

    private final double PADDING = 12; // WORKAROUND

    private double ICON_SIZE = 48;

    public GlyphsBrowser(GlyphsBrowserAppModel glyphPacksModel) {
        this.model = glyphPacksModel;
        init();
    }

    private void init() {
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle(GlyphsBrowserAppModel.RESOURCE_BUNDLE);
            URL fxmlURL = getClass().getResource(GlyphsBrowserAppModel.GLYPH_BROWSER_FXML);
            FXMLLoader fxmlLoader = new FXMLLoader(fxmlURL, resourceBundle);
            fxmlLoader.setRoot(this);
            fxmlLoader.setController(this);
            fxmlLoader.load();
        } catch (IOException ex) {
            Logger.getLogger(GlyphsBrowser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    Callback<TableColumn.CellDataFeatures<List<GlyphIcon>, Button>, ObservableValue<Button>> cellValueFactory = param -> {
        Button btn = new Button();
        TableColumn<List<GlyphIcon>, Button> column = param.getTableColumn();
        int cols = glyphsTableView.getColumns().indexOf(column);
        if (cols >= param.getValue().size()) {
            return null;
        }
        btn.setPrefWidth(ICON_SIZE);
        btn.setPrefHeight(ICON_SIZE);
        btn.setGraphic(param.getValue().get(cols));
        return new SimpleObjectProperty<>(btn);
    };

    @FXML
    void initialize() {
        glyphsTableView.getSelectionModel().setCellSelectionEnabled(false);
        glyphsTableView.widthProperty().addListener((observable, oldValue, newValue) -> {
            ICON_SIZE = glyphSizeSlider.getValue() + PADDING * 2;
            MAX_COLS = (int) (newValue.doubleValue() / ICON_SIZE);
            refreshTableView();
        });

        //glyphsGridView.cellHeightProperty().bind(model.glyphSizeProperty());
        //glyphsGridView.cellWidthProperty().bind(model.glyphSizeProperty());
        glyphsTableView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getTarget() instanceof GlyphIcon) {
                model.selectedGlyphIconProperty().set((GlyphIcon) event.getTarget());
            }
        });
        fontUrlLabel.setOnAction((ActionEvent t) -> {
            if (model.getHostServices() != null) {
                model.getHostServices().showDocument(fontUrlLabel.getText());
            }
        });
        glyphSizeSlider.valueProperty().bindBidirectional(model.glyphSizeProperty());
        glyphSizeSliderValueLabel.textProperty().bind(glyphSizeSlider.valueProperty().asString("%.0f"));
        glyphsPackListView.setItems(model.getGlyphsPacks());
        glyphsPackListView.itemsProperty().addListener((Observable observable) -> {
            glyphsPackListView.getSelectionModel().selectFirst();
        });
        glyphsPackListView.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends GlyphsPack> observable, GlyphsPack oldValue, GlyphsPack newValue) -> {

            //Reset Search Bar
//            searchBar.setText("");

            //
            refreshTableView();
        });
        glyphsPackListView.getSelectionModel().selectFirst();
        model.selectedGlyphIconProperty().addListener((ObservableValue<? extends GlyphIcon> observable, GlyphIcon oldValue, GlyphIcon newValue) -> {
            Optional<GlyphIconInfo> value = model.getGlyphIconInfo(newValue);
            if (value.isPresent()) {
                showGlyphIconsDetails(value.get());
            }
        });
        copyCodeButton.visibleProperty().bind(glyphCodeLabel.textProperty().isEmpty().not());
        copyFactoryCodeButton.visibleProperty().bind(glyphFactoryCodeLabel.textProperty().isEmpty().not());
        //== searchBar
        searchBar.textProperty().addListener((observable, oldValue, newValue) -> {
            //In case search bar has no text inside
            if (searchBar.getText().isEmpty()) {
                //Reset Search Bar Found Label
                searchBarResultsLabel.setText("Found : [ All ]");

                //Show all the items on the TableView
                updateBrowser(glyphsPackListView.getSelectionModel().getSelectedItem());
            }
            else { //Let's do some search magic
                ObservableList<GlyphIcon> glyphNodes = glyphsPackListView.getSelectionModel().getSelectedItem().getGlyphNodes();
                updateIcons(glyphNodes, newValue);
            }
        });
        this.setOnKeyPressed(event -> {
            KeyCodeCombination kc = new KeyCodeCombination(KeyCode.F, KeyCombination.META_DOWN);
            if (kc.match(event)){
                event.consume();
                searchBar.requestFocus();
            }
        });
    }

    private void refreshTableView() {
        System.out.println("Load icons by columns: " + MAX_COLS);
        glyphsTableView.getItems().clear();
        glyphsTableView.getColumns().clear();
        for (int i = 0; i < MAX_COLS; i++) {
            TableColumn<List<GlyphIcon>, Button> col = new TableColumn<>(String.valueOf(i));
            col.setSortable(false);
            col.setEditable(false);
            col.setPrefWidth(ICON_SIZE);
            glyphsTableView.getColumns().add(col);
            col.setCellValueFactory(cellValueFactory);
        }
        updateBrowser(glyphsPackListView.getSelectionModel().getSelectedItem());
        glyphsTableView.scrollTo(0);
    }

    private void showGlyphIconsDetails(GlyphIconInfo glyphIconInfo) {
        if (glyphIconInfo != null) {
            {
                glyphNameLabel.setText(glyphIconInfo.getGlyphNameName());
                glyphCodeLabel.setText(glyphIconInfo.getGlyphCode());
                glyphFactoryCodeLabel.setText(glyphIconInfo.getGlyphFactoryCode());
                glyphPreviewPane.getChildren().setAll(glyphIconInfo.getPreviewGlyphs());
            }
        }
    }

    private void clearGlyphIconsDetails() {
        glyphPreviewPane.getChildren().clear();
        glyphNameLabel.setText("");
        glyphCodeLabel.setText("");
        glyphFactoryCodeLabel.setText("");
    }

    private void updateBrowser(GlyphsPack glyphPack) {
        clearGlyphIconsDetails();
        ObservableList<GlyphIcon> glyphNodes = glyphPack.getGlyphNodes();
        this.updateIcons(glyphNodes, searchBar.getText());

        numberOfIconsLabel.setText(glyphPack.getNumberOfIcons() + "");
        fontNameLabel.setText(glyphPack.getName());
        fontFamilyLabel.setText(glyphPack.getFamiliy());
        fontVersionLabel.setText(glyphPack.getVersion());
        fontLicenseLabel.setText(glyphPack.getLicense());
        fontReleaseDateLabel.setText(glyphPack.getReleaseDate());
        fontUrlLabel.setText(glyphPack.getURL());
        fontWhatsNewLabel.setText(glyphPack.getWhatsNew());
        if (!glyphPack.getGlyphNodes().isEmpty()) {
            Optional<GlyphIconInfo> value = model.getGlyphIconInfo(glyphPack.getGlyphNodes().get(0));
            if (value.isPresent()) {
                showGlyphIconsDetails(value.get());
            }
        }
        model.selectedGlyphIconProperty().set(glyphPack.getGlyphNodes().get(0));
    }

    private void updateIcons(ObservableList<GlyphIcon> glyphNodes, String filter) {
        glyphsTableView.getItems().clear();
        List<GlyphIcon> filtered = null;
        if (filter != null && !"".equals(filter.trim())) {
            filtered = glyphNodes.filtered(glyphIcon -> {
                //Glyph name contains search bar text ? [ No case sensitive ]
                String searchValue = filter.toLowerCase(); //Speed improvements
                return glyphIcon.getGlyphName().toLowerCase().contains(searchValue); //visible only if name matches searchValue
            }).stream().toList();
            // Add the new items
            glyphsTableView.getItems().clear();
        }
        else {
            filtered = glyphNodes.stream().toList();
        }
        searchBarResultsLabel.setText("Found : [ " + filtered.size() + " ]");
        List<List<GlyphIcon>> grid = new ArrayList<>();
        int rowIdx = 0;
        int colIdx = 0;
        for (int i = 0; i < filtered.size(); i++) {
            GlyphIcon glyphNode = filtered.get(i);
            colIdx = i % MAX_COLS;
            if (colIdx == 0) {
                rowIdx = i / MAX_COLS;
                grid.add(rowIdx, new ArrayList<>());
            }
            List<GlyphIcon> row = grid.get(rowIdx);
            row.add(glyphNode);
        }
        for (List<GlyphIcon> glyphIcons : grid) {
            glyphsTableView.getItems().add(glyphIcons);
        }
    }

    @FXML
    public void onClearSearch() {
        searchBar.clear();
    }

    @FXML
    public void onCopyUnicode() {
        final ClipboardContent content = new ClipboardContent();
        content.putString(model.selectedGlyphIconProperty().getValue().unicode());
        model.getClipboard().setContent(content);
    }

    @FXML
    public void onCopyCode() {
        final ClipboardContent content = new ClipboardContent();
        content.putString(glyphCodeLabel.getText());
        model.getClipboard().setContent(content);
    }

    @FXML
    public void onCopyFactoryCode() {
        final ClipboardContent content = new ClipboardContent();
        content.putString(glyphFactoryCodeLabel.getText());
        model.getClipboard().setContent(content);
    }

}
