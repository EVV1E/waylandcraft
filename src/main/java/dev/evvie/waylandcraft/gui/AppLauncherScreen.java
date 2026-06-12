package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftCommon;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AppLauncherScreen extends Screen {
	
	private WaylandCraft wlc;
	private AppListWidget list;
	private CategorySelectorWidget categorySelector;
	private EditBox searchBox;
	
	private Component header;
	
	private ArrayList<Category> categories;
	
	public AppLauncherScreen(WaylandCraft wlc) {
		super(Component.literal("App Launcher"));
		
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
		int listWidth = AppListWidget.ELEMENT_WIDTH;
		int listHeight = 170;
		int listX = width / 2 - listWidth / 2;
		int listY = height / 2 - listHeight / 2;
		
		list = new AppListWidget(this::launch, Component.literal("App List"));
		list.setRectangle(listWidth, listHeight, listX, listY);
		this.addRenderableWidget(this.list);
		
		// Search box is not added to widgets for custom focus / key enter rules
		searchBox = new EditBox(font, listX, listY - 25, listWidth, 20, Component.literal("Search"));
		searchBox.setResponder(this::filterSetEntries);
		searchBox.setFocused(true); // Eternally focused
		
		createCategories();
		
		int categoryButtonSize = 19;
		int catSelW = categoryButtonSize * 2;
		List<CategorySelectorWidget.Entry> categoryEntries = categories.stream().map((category) -> new CategorySelectorWidget.Entry(category.title, category.icon)).toList();
		categorySelector = new CategorySelectorWidget(Component.literal("Categories"), this::filterSetCategory, categoryEntries);
		categorySelector.setElementSize(categoryButtonSize);
		categorySelector.setRectangle(catSelW, listHeight + 25, listX - catSelW - 10, listY - 25);
		this.addRenderableWidget(categorySelector);
		
		filterSetEntries(null);
	}
	
	private void clearSearch() {
		searchBox.setResponder(null);
		searchBox.setValue("");
		searchBox.setResponder(this::filterSetEntries);
	}
	
	private void filterSetCategory(int idx) {
		clearSearch();
		if(idx < 0) return;
		
		Category category = categories.get(idx);
		List<DesktopEntry> entries = wlc.xdgManager.entries().stream()
				.filter((e) -> e.visible && Arrays.stream(e.categories).anyMatch((c) -> c.equals(category.name)))
				.toList();
		list.setEntries(entries);
		header = category.title;
	}
	
	private int similarityScore(String hay, String needle) {
		if(hay == null || needle == null) return 0;
		hay = hay.toLowerCase();
		needle = needle.toLowerCase();
		
		if(hay.equals(needle)) return 3;
		if(hay.startsWith(needle)) return 2;
		if(hay.contains(needle)) return 1;
		return 0;
	}
	
	private int sumSimilarityScore(String[] hays, String needle) {
		int score = 0;
		for(String hay : hays) {
			score += similarityScore(hay, needle);
		}
		return score;
	}
	
	private int entryMatchesStrScore(DesktopEntry entry, String str) {
		if(!entry.visible) return -1;
		int score = 0;
		score += 3 * similarityScore(entry.name, str);
		score += sumSimilarityScore(entry.keywords, str);
		score += similarityScore(entry.comment, str);
		score += similarityScore(entry.genericName, str);
		score += similarityScore(entry.exec, str);
		return score;
	}
	
	private void filterSetEntries(String filter) {
		categorySelector.unselect();
		List<DesktopEntry> entries;
		if(filter == null || filter.equals("")) {
			entries = wlc.xdgManager.entries().stream().filter((e) -> e.visible).toList();
		}
		else {
			entries = wlc.xdgManager.entries().stream()
					.map((entry) -> new RankedDesktopEntry(entry, entryMatchesStrScore(entry, filter)))
					.filter((r) -> r.score > 0)
					.sorted((r1, r2) -> r2.score - r1.score)
					.map((r) -> r.entry)
					.toList();
		}
		list.setEntries(entries);
		header = Component.literal("Search");
	}
	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if(searchBox.keyPressed(event)) return true;
		return super.keyPressed(event);
	}
	
	@Override
	public boolean charTyped(CharacterEvent event) {
		if(searchBox.charTyped(event)) return true;
		return super.charTyped(event);
	}
	
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
		searchBox.extractRenderState(context, mouseX, mouseY, partialTicks);
		
		if(searchBox.getY() >= 5 + font.lineHeight + 5) {
			context.text(font, header, width / 2 - font.width(header) / 2, 5, Color.white.getRGB());
		}
	}
	
	public void launch(DesktopEntry entry) {
		wlc.bridge.execApp(entry.appId);
		this.onClose();
	}
	
	private void createCategories() {
		this.categories = new ArrayList<Category>();
		categories.add(new Category("AudioVideo", Component.literal("Multimedia"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/multimedia"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Audio", Component.literal("Audio"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/music"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Video", Component.literal("Video"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/video"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Development", Component.literal("Development"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/development"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Education", Component.literal("Education"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/education"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("HealthFitness", Component.literal("Health and Fitness"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/healthfitness"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Game", Component.literal("Games"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/game"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Graphics", Component.literal("Graphics"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/graphics"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Network", Component.literal("Network"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/network"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Office", Component.literal("Office"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/office"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Science", Component.literal("Science"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/science"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Settings", Component.literal("Settings"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/settings"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("System", Component.literal("System"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/system"), new ArrayList<DesktopEntry>()));
		categories.add(new Category("Utility", Component.literal("Utility"), Identifier.fromNamespaceAndPath(WaylandCraftCommon.MOD_ID, "categories/utility"), new ArrayList<DesktopEntry>()));
	}
	
	private static record RankedDesktopEntry(DesktopEntry entry, int score) {}
	private static record Category(String name, Component title, Identifier icon, ArrayList<DesktopEntry> entries) {}
	
}
