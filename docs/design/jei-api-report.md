# JEI 15.20.0.106 API reference (MC 1.20.1 / Forge)

Target artifact: `mezz.jei:jei-1.20.1-forge:15.20.0.106`.

## Provenance (read this first)

The GitHub branch `1.20.1` **is not this version**. Its HEAD contains additions from 15.3x–15.7x
(`IRecipeManager.getRecipeIngredients` @since 15.71.0, `createRecipeLayoutDrawableOrShowError` @since 15.35.0,
`getRecipeButtonControllerFactories` @since 15.38.0, the `mezz.jei.api.gui.buttons` package, `IBookmarkManager`),
and there is **no git tag** named `15.20.0.106` (the repo's tags are the old `v29`–`v31` scheme), so
`raw.githubusercontent.com/.../15.20.0.106/...` 404s.

All signatures below were read from the published sources jar
`https://maven.blamejared.com/mezz/jei/jei-1.20.1-forge/15.20.0.106/jei-1.20.1-forge-15.20.0.106-sources.jar`,
extracted to `D:\dsh workspace\Quickly Pattern\jei\src` (764 `.java` files: `mezz/jei/api`, `mezz/jei/common`,
`mezz/jei/core`, `mezz/jei/forge`, `mezz/jei/gui`, `mezz/jei/library`).

Paths are given as jar paths (`mezz/jei/...`). GitHub equivalents on branch `1.20.1` prefix the module dir
(content may be newer than 15.20.0.106 — use only as a rough cross-check):

| jar path | GitHub source path |
|---|---|
| `mezz/jei/api/...` | `CommonApi/src/main/java/mezz/jei/api/...` |
| `mezz/jei/api/forge/...` | `ForgeApi/src/main/java/mezz/jei/api/forge/...` |
| `mezz/jei/common/...` | `Common/src/main/java/mezz/jei/common/...` |
| `mezz/jei/library/...` | `Library/src/main/java/mezz/jei/library/...` |
| `mezz/jei/gui/...` | `Gui/src/main/java/mezz/jei/gui/...` |
| `mezz/jei/forge/...` | `Forge/src/main/java/mezz/jei/forge/...` |

---

## 1. `mezz.jei.api.runtime.IJeiRuntime`

Path: `mezz/jei/api/runtime/IJeiRuntime.java` (108 lines). Complete method list, verbatim:

```java
IRecipeManager getRecipeManager();
IRecipesGui getRecipesGui();
IIngredientFilter getIngredientFilter();
IIngredientListOverlay getIngredientListOverlay();
IBookmarkOverlay getBookmarkOverlay();
IJeiHelpers getJeiHelpers();
IIngredientManager getIngredientManager();
@Deprecated(since = "15.19.1", forRemoval = true)
default IIngredientVisibility getIngredientVisibility() { return getJeiHelpers().getIngredientVisibility(); }
IJeiKeyMappings getKeyMappings();
IScreenHelper getScreenHelper();
IRecipeTransferManager getRecipeTransferManager();
IEditModeConfig getEditModeConfig();
IJeiConfigManager getConfigManager();
```

Notes:
- `getConfigManager()` is **not** deprecated in 15.20.0.106.
- The interface is **not** annotated `@ApiStatus.NonExtendable` here.
- `IBookmarkManager` / `getBookmarkManager()` do **not** exist in 15.20.0.106 (newer versions only).
- Obtain the runtime by implementing `IModPlugin.onRuntimeAvailable(IJeiRuntime jeiRuntime)`.

---

## 2. `mezz.jei.api.recipe.IRecipeManager`

Path: `mezz/jei/api/recipe/IRecipeManager.java` (218 lines). Complete method list:

```java
<R> IRecipeLookup<R> createRecipeLookup(RecipeType<R> recipeType);                 // @since 9.5.0
IRecipeCategoriesLookup createRecipeCategoryLookup();                              // @since 9.5.0
<T> IRecipeCategory<T> getRecipeCategory(RecipeType<T> recipeType);                // @since 15.5.0
IRecipeCatalystLookup createRecipeCatalystLookup(RecipeType<?> recipeType);        // @since 9.5.0
<T> void hideRecipes(RecipeType<T> recipeType, Collection<T> recipes);
<T> void unhideRecipes(RecipeType<T> recipeType, Collection<T> recipes);
<T> void addRecipes(RecipeType<T> recipeType, List<T> recipes);
void hideRecipeCategory(RecipeType<?> recipeType);
void unhideRecipeCategory(RecipeType<?> recipeType);
<T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(
        IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup);      // @since 11.5.0
<T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(
        IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup,
        IScalableDrawable background, int borderSize);                            // @since 15.7.0
IRecipeSlotDrawable createRecipeSlotDrawable(RecipeIngredientRole role,
        List<Optional<ITypedIngredient<?>>> ingredients, Set<Integer> focusedIngredients,
        int ingredientCycleOffset);                                               // @since 15.20.0
@Deprecated(since = "15.20.0")
default IRecipeSlotDrawable createRecipeSlotDrawable(RecipeIngredientRole role,
        List<Optional<ITypedIngredient<?>>> ingredients, Set<Integer> focusedIngredients,
        int xPos, int yPos, int ingredientCycleOffset);                           // delegates + setPosition
<T> Optional<RecipeType<T>> getRecipeType(ResourceLocation recipeUid, Class<? extends T> recipeClass); // @since 15.13.0
Optional<RecipeType<?>> getRecipeType(ResourceLocation recipeUid);                 // @since 11.2.3
```

**Does not exist in 15.20.0.106:** `getRecipeIngredients(...)`, `createRecipeLayoutDrawableOrShowError(...)`,
`getRecipeButtonControllerFactories()`.

Lookup interfaces (`mezz/jei/api/recipe/`):

```java
// IRecipeLookup.java
IRecipeLookup<R> limitFocus(Collection<? extends IFocus<?>> focuses);
IRecipeLookup<R> includeHidden();
Stream<R> get();

// IRecipeCategoriesLookup.java
IRecipeCategoriesLookup limitTypes(Collection<RecipeType<?>> recipeTypes);
IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> focuses);
IRecipeCategoriesLookup includeHidden();
Stream<IRecipeCategory<?>> get();

// IRecipeCatalystLookup.java
IRecipeCatalystLookup includeHidden();
Stream<ITypedIngredient<?>> get();
<S> Stream<S> get(IIngredientType<S> ingredientType);
default Stream<ItemStack> getItemStack() { return get(VanillaTypes.ITEM_STACK); }
```

Semantics (verified in `mezz/jei/library/recipes/RecipeLookup.java`, `RecipeCategoriesLookup.java`,
`RecipeCatalystLookup.java`, `RecipeManagerInternal.java`):
- `createRecipeLookup(type).get()` → all recipes of that type, filtered by focus/hidden state.
- `.includeHidden()` is what un-hides recipes **and** categories (`RecipeManagerInternal#getRecipesStream`,
  `#getRecipeCategoriesForTypes` receive the flag; hidden filtering is skipped).

### "From a displayed recipe, get its RecipeType and its IRecipeCategory"

- If you hold an `IRecipeLayoutDrawable` (returned by `createRecipeLayoutDrawable`, or your own click handler):
  `drawable.getRecipeCategory()` → `IRecipeCategory<T>`, `drawable.getRecipeCategory().getRecipeType()` →
  `RecipeType<T>`, `drawable.getRecipe()` → the recipe. (`mezz/jei/api/gui/IRecipeLayoutDrawable.java`.)
- Given only a recipe object, there is **no direct** `getRecipeType(recipe)` API. Options:
  1. id-based: `category.getRegistryName(recipe)` (`@Nullable ResourceLocation`) then
     `IRecipeManager.getRecipeType(uid, SomeRecipe.class)` (or `IJeiHelpers.getRecipeType(uid, cls)`);
  2. class scan: iterate `IJeiHelpers.getAllRecipeTypes()` and test
     `rt.getRecipeClass().isInstance(recipe)`, then confirm membership with
     `createRecipeLookup(rt).includeHidden().get().anyMatch(r -> r == recipe)`.
  Note `getRecipeType(ResourceLocation recipeUid)`'s parameter is really the **RecipeType uid**
  (`@see RecipeType#getUid()`), despite the name.
- Reading the recipe *currently shown* by JEI's screen: `IRecipesGui` exposes no getter.
  `mezz.jei.gui.recipes.RecipesGui` holds it privately (fields `IRecipeGuiLogic logic`,
  `RecipeGuiLayouts layouts`); `IRecipeGuiLogic.getSelectedRecipeCategory()` and
  `List<RecipeLayoutWithButtons<?>> getVisibleRecipeLayoutsWithButtons(...)` are public but the screen does not
  expose them → needs Mixin/AT (see §6).

---

## 3. `RecipeType` and `IRecipeCategory`

`mezz/jei/api/recipe/RecipeType.java` (67 lines):

```java
public final class RecipeType<T> {
    public static <T> RecipeType<T> create(String nameSpace, String path, Class<? extends T> recipeClass);
    public RecipeType(ResourceLocation uid, Class<? extends T> recipeClass);
    public ResourceLocation getUid();
    public Class<? extends T> getRecipeClass();
    // equals/hashCode over (recipeClass, uid); toString(): "RecipeType[uid=..., recipeClass=...]"
}
```

`mezz/jei/api/recipe/category/IRecipeCategory.java` (252 lines):

```java
public interface IRecipeCategory<T> {
    RecipeType<T> getRecipeType();
    Component getTitle();
    @Deprecated(since = "15.20.0", forRemoval = true) @Nullable default IDrawable getBackground() { return null; }
    default int getWidth();      // throws IllegalStateException if background is null
    default int getHeight();     // throws IllegalStateException if background is null
    @Nullable IDrawable getIcon();
    void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses);
    default void createRecipeExtras(IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses);
    default void draw(T recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY);
    default void onDisplayedIngredientsUpdate(T recipe, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses);
    @Deprecated List<Component> getTooltipStrings(T recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY);
    default void getTooltip(ITooltipBuilder tooltip, T recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY);
    @Deprecated(since = "15.9.0", forRemoval = true)
    default boolean handleInput(T recipe, double mouseX, double mouseY, InputConstants.Key input);
    default boolean isHandled(T recipe);
    @Nullable default ResourceLocation getRegistryName(T recipe);
}
```

Answers to the specific points:
- Type parameter is `IRecipeCategory<T>` where `T` is the recipe class. No `IRecipeCategory<R, C>` two-param form.
- `getUid()` does **not** exist on `IRecipeCategory`. The category uid is `getRecipeType().getUid()`.
- `getRecipeClass()` does **not** exist on `IRecipeCategory`; it is `getRecipeType().getRecipeClass()`.
- **Catalyst method: neither `getCatalysts()` nor `getRecipeCatalysts()` exists on `IRecipeCategory` in
  15.20.0.106** — and it does not exist on branch HEAD either (checked). Catalysts are only *registered*
  (`IRecipeCatalystRegistration`, §7) and *queried* (`IRecipeManager.createRecipeCatalystLookup`, §5).
  `IRecipeCategory` has **no** `needsRecipeBorder()` and no `getSourceRecipeIds()` in this version.
- `@since 15.20.0` convenience base class exists: `mezz/jei/api/recipe/category/AbstractRecipeCategory.java`
  `public AbstractRecipeCategory(RecipeType<T> recipeType, Component title, IDrawable icon, int width, int height)`.
- Built-in types: `mezz/jei/api/constants/RecipeTypes.java` — `CRAFTING`, `STONECUTTING`, `SMELTING`, `SMOKING`,
  `BLASTING`, `CAMPFIRE_COOKING`, `FUELING`, `BREWING`, `ANVIL`, `SMITHING`, `COMPOSTING`, `INFORMATION`.

---

## 4. Reading the full ingredient layout for `IRecipeCategory<T>` + `T recipe`

Roles — `mezz/jei/api/recipe/RecipeIngredientRole.java`: `enum RecipeIngredientRole { INPUT, OUTPUT, CATALYST, RENDER_ONLY }`
(`RENDER_ONLY` = drawn/navigable, ignored by recipe lookups).

The only public way to materialize a layout is:

```java
<T> Optional<IRecipeLayoutDrawable<T>> IRecipeManager.createRecipeLayoutDrawable(
        IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup);

<T> Optional<IRecipeLayoutDrawable<T>> IRecipeManager.createRecipeLayoutDrawable(
        IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focusGroup,
        IScalableDrawable background, int borderSize);
```

(`mezz/jei/api/recipe/IRecipeManager.java` lines 120–153.) Implementation:
`mezz/jei/library/gui/recipes/RecipeLayout.java` calls `recipeCategory.setRecipe(builder, recipe, focuses)`,
`builder.buildRecipeLayout(...)`, then `recipeCategory.createRecipeExtras(recipeLayout, recipe, focuses)`, and
returns `Optional.empty()` (logging `"Error caught from Recipe Category: {}"`) on `RuntimeException | LinkageError`.

Then read slots — `mezz/jei/api/gui/ingredient/IRecipeSlotsView.java`:

```java
@Unmodifiable List<IRecipeSlotView> getSlotViews();
default List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role);   // filters slotView.getRole() == role
default Optional<IRecipeSlotView> findSlotByName(String slotName);
```

and each slot — `mezz/jei/api/gui/ingredient/IRecipeSlotView.java` (`@ApiStatus.NonExtendable`):

```java
Stream<ITypedIngredient<?>> getAllIngredients();
Optional<ITypedIngredient<?>> getDisplayedIngredient();
RecipeIngredientRole getRole();
default <T> Stream<T> getIngredients(IIngredientType<T> ingredientType);
default Stream<ItemStack> getItemStacks();
default boolean isEmpty();
default Optional<ItemStack> getDisplayedItemStack();
default <T> Optional<T> getDisplayedIngredient(IIngredientType<T> ingredientType);
Optional<String> getSlotName();
```

Canonical snippet:

```java
IJeiHelpers helpers = runtime.getJeiHelpers();
IFocusGroup empty = helpers.getFocusFactory().getEmptyFocusGroup();
IRecipeCategory<T> cat = runtime.getRecipeManager().getRecipeCategory(type);
Optional<IRecipeLayoutDrawable<T>> opt = runtime.getRecipeManager().createRecipeLayoutDrawable(cat, recipe, empty);
opt.ifPresent(layout -> {
    IRecipeSlotsView slots = layout.getRecipeSlotsView();
    List<IRecipeSlotView> inputs  = slots.getSlotViews(RecipeIngredientRole.INPUT);
    List<IRecipeSlotView> outputs = slots.getSlotViews(RecipeIngredientRole.OUTPUT);
    List<IRecipeSlotView> cats    = slots.getSlotViews(RecipeIngredientRole.CATALYST);
    List<ITypedIngredient<?>> all = inputs.stream().flatMap(IRecipeSlotView::getAllIngredients).toList();
});
```

Positions: `IRecipeSlotView` has **no** x/y. Slot positions are only available from
`IRecipeSlotDrawable` (`mezz/jei/api/gui/ingredient/IRecipeSlotDrawable.java`):
`Rect2i getAreaIncludingBackground()` (`@since 15.20.0`), `void setPosition(int x, int y)`,
`boolean isMouseOver(double, double)`. Those drawables are reachable from
`IRecipeExtrasBuilder.getRecipeSlots()` → `IRecipeSlotDrawablesView`
(`@Unmodifiable List<IRecipeSlotDrawable> getSlots(); default List<IRecipeSlotDrawable> getSlots(RecipeIngredientRole);`)
inside `IRecipeCategory.createRecipeExtras(...)`, or by hit-testing a live layout:
`IRecipeLayoutDrawable.getSlotUnderMouse(double, double)` → `Optional<RecipeSlotUnderMouse>` where
`mezz/jei/api/gui/inputs/RecipeSlotUnderMouse.java` is
`public record RecipeSlotUnderMouse(IRecipeSlotDrawable slot, ScreenPosition offset)` with
`addOffset(int, int)` and `isMouseOver(double, double)`.

Related drawable API (`mezz/jei/api/gui/IRecipeLayoutDrawable.java`): `setPosition`, `drawRecipe(GuiGraphics, int, int)`,
`drawOverlays(GuiGraphics, int, int)`, `getRect()`, `getRectWithBorder()`, `getRecipeTransferButtonArea()`,
`getRecipeBookmarkButtonArea()`, `getRecipeSlotsView()`, `getRecipeCategory()`, `getRecipe()`, `getInputHandler()`, `tick()`.

---

## 5. "All recipes a workbench can make" — catalyst → RecipeTypes → recipes

Public chain (all API, no mixin):

```java
IIngredientManager im = runtime.getIngredientManager();
ITypedIngredient<ItemStack> typed =
        im.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CRAFTING_TABLE)).orElseThrow();
IFocus<ItemStack> catalystFocus = runtime.getJeiHelpers().getFocusFactory()
        .createFocus(RecipeIngredientRole.CATALYST, typed);

List<IRecipeCategory<?>> categories = runtime.getRecipeManager()
        .createRecipeCategoryLookup()          // IRecipeCategoriesLookup
        .limitFocus(List.of(catalystFocus))    // focus role CATALYST => only catalyst relationships
        .includeHidden()                       // include hidden categories
        .get()                                 // Stream<IRecipeCategory<?>>
        .toList();

// one RecipeType per category:
RecipeType<?> type = categories.get(0).getRecipeType();

// ALL recipes of that type/category, including hidden ones:
List<?> all = runtime.getRecipeManager()
        .createRecipeLookup((RecipeType) type) // or a generic helper below
        .includeHidden()
        .get()
        .toList();
```

Relevant signatures:
- `IIngredientManager.createTypedIngredient(V ingredient)` → `default <V> Optional<ITypedIngredient<V>> createTypedIngredient(V ingredient);`
  (`@since 15.2.0`; the typed overload `createTypedIngredient(IIngredientType<V>, V)` is `@since 11.5.0`).
  There is **no** `getTypedIngredient(...)` in 15.20.0.106. (`mezz/jei/api/runtime/IIngredientManager.java`.)
- `IFocusFactory.createFocus(RecipeIngredientRole role, ITypedIngredient<V> typedIngredient)` and
  `IFocusFactory.createFocus(RecipeIngredientRole, IIngredientType<V>, V)`; `getEmptyFocusGroup()`,
  `createFocusGroup(Collection<? extends IFocus<?>>)`. (`mezz/jei/api/recipe/IFocusFactory.java`.)
- `createRecipeCategoryLookup()` / `limitFocus(...)` / `includeHidden()` / `get()` as in §2.

Generic helper (avoids wildcard capture on `RecipeType<?>`):

```java
private static <R> List<R> allRecipes(IRecipeManager mgr, RecipeType<R> type) {
    return mgr.createRecipeLookup(type).includeHidden().get().toList();
}
```

Why this is faithful to JEI's "Uses"/"Recipes" tabs (implementation evidence):
- `mezz/jei/library/recipes/InternalRecipeManagerPlugin.java` line 35:
  `<V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus)` → `recipeMaps.get(focus.getRole()).getRecipeTypes(focus.getTypedValue())`.
- `mezz/jei/library/recipes/collect/RecipeMap.java` line 42: `getRecipeTypes(ITypedIngredient<T>)` concatenates
  `ingredientUidToCategoryMap` (recipes containing the ingredient) with `categoryCatalystUidToRecipeCategoryMap`
  (categories the ingredient is a catalyst of) — the role selects which map is used.
- `RecipeManagerInternal.getRecipeCategoriesUncached` (lines 210–238) maps focus→recipeTypes→categories and only
  filters hidden ones when `!includeHidden`.
- `RecipeManagerInternal.isRecipeCatalyst(RecipeType<?>, IFocus<?>)` (line 299) exists but is **not** API.

To *display* the result exactly like the tab, call
`IRecipesGui.showRecipes(IRecipeCategory<T> recipeCategory, List<T> recipes, List<IFocus<?>> focuses)` or
`IRecipesGui.show(List<IFocus<?>> focuses)` (`mezz/jei/api/runtime/IRecipesGui.java` — only `show(IFocus)`,
`show(List<IFocus<?>>)`, `showTypes(List<RecipeType<?>>)`, `showRecipes(...)`, `getIngredientUnderMouse(IIngredientType<T>)`).

---

## 6. Adding custom buttons to the JEI recipe screen — **no public API**

**Does not exist in 15.20.0.106:** `IRecipeGuiAddon`, `IRecipeScreenAddon`, `IRecipeButtonControllerFactory`,
`IIconButtonController`, `getRecipeButtonControllerFactories()`, any `addButton`/`registerAddon` on `IRecipesGui`.
There is no `mezz.jei.api.gui.buttons` package at all in this version.

What actually exists in `mezz/jei/api/gui/handlers/` (complete directory listing):

| File | Purpose |
|---|---|
| `IGuiContainerHandler.java` | `getGuiExtraAreas(T)`, `getClickableIngredientUnderMouse(T, double, double)`, `getGuiClickableAreas(T, double, double)` — container-screen integration |
| `IGlobalGuiHandler.java` | global extra areas / clickable ingredients |
| `IGuiClickableArea.java` | clickable region → opens recipes |
| `IGhostIngredientHandler.java` | drag ghost ingredients |
| `IGuiProperties.java` | gui size record |
| `IScreenHandler.java` | `public interface IScreenHandler<T extends Screen> extends Function<T, IGuiProperties>` — converts a mod screen to JEI gui properties; **not** widgets |

Nearest official extension points (inside a recipe layout, not the screen chrome):
- `IRecipeCategory.createRecipeExtras(IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses)` →
  `IRecipeExtrasBuilder.addWidget(IRecipeWidget widget)`, `addGuiEventListener(IJeiGuiEventListener listener)`,
  `addDrawable(IDrawable, int, int)`, `addInputHandler(IJeiInputHandler)`
  (`mezz/jei/api/gui/widgets/IRecipeExtrasBuilder.java`, `IRecipeWidget.java`, `mezz/jei/api/gui/inputs/IJeiGuiEventListener.java`).
- Cross-category equivalent for drawing/tooltips: `IRecipeCategoryDecorator<T>` via
  `IAdvancedRegistration.addRecipeCategoryDecorator(RecipeType<T>, IRecipeCategoryDecorator<T>)`
  (`mezz/jei/api/recipe/category/extensions/IRecipeCategoryDecorator.java`; `draw(...)` + `decorateTooltips(...)` only — no widget API).

The recipe screen class **is** public:

```java
package mezz.jei.gui.recipes;
public class RecipesGui extends Screen implements IRecipesGui, IRecipeFocusSource {
    public RecipesGui(
        IRecipeManager recipeManager,
        IRecipeTransferManager recipeTransferManager,
        IIngredientManager ingredientManager,
        IInternalKeyMappings keyBindings,          // mezz.jei.common.input — internal type
        IFocusFactory focusFactory,
        BookmarkList bookmarks,                    // mezz.jei.gui.bookmarks — internal type
        IGuiHelper guiHelper
    );
}
```

(`mezz/jei/gui/recipes/RecipesGui.java` line 58 class, line 111 constructor; the compiled class
`mezz/jei/gui/recipes/RecipesGui.class` is present in `jei-1.20.1-forge-15.20.0.106.jar`, so a mod can compile
against it, but the constructor is not constructible from API types.)

Public methods on it (complete): `getArea(): ImmutableRect2i`, `getLeftSideExtraWidth()`, `isPauseScreen()`,
`init()`, `render(GuiGraphics,int,int,float)`, `tick()`, `isMouseOver(double,double)`,
`getIngredientUnderMouse(double,double)`, `getDraggableIngredientUnderMouse(double,double)`,
`mouseMoved`, `mouseDragged`, `mouseScrolled`, `mouseClicked`, `mouseReleased`, `keyPressed`,
`isOpen()`, `onClose()`, `show(List<IFocus<?>>)`, `showTypes(List<RecipeType<?>>)`,
`showRecipes(IRecipeCategory<T>, List<T>, List<IFocus<?>>)`, `getIngredientUnderMouse(IIngredientType<T>)`,
`back()`, `getProperties()`. **No** `addButton`/`addWidget`/`addRenderableWidget`/`registerAddon`.

Private state you would need to mixin into: `IRecipeGuiLogic logic`, `RecipeGuiLayouts layouts`,
`RecipeCatalysts recipeCatalysts`, `RecipeGuiTabs recipeGuiTabs`, `RecipeOptionButtons optionButtons`,
`UserInputRouter inputHandler`, `GuiIconButton nextRecipeCategory/previousRecipeCategory/nextPage/previousPage`,
`DrawableNineSliceTexture background`, `RecipeCategoryTitle recipeCategoryTitle`.
Buttons (transfer/bookmark/paging) are created inside the constructor / `createRecipeLayoutWithButtons`;
there is no list of addon buttons to append to.

**Conclusion:** a Mixin (or Forge `ScreenEvent`) is required.
- Mixin into `mezz.jei.gui.recipes.RecipesGui` (`init()` / `render(...)` / `mouseClicked(...)`) and add your own
  state; you have direct field access to `logic`/`layouts`, and can get the selected category via
  `IRecipeGuiLogic.getSelectedRecipeCategory()` and layouts via
  `List<RecipeLayoutWithButtons<?>> getVisibleRecipeLayoutsWithButtons(...)`
  (`mezz/jei/gui/recipes/IRecipeGuiLogic.java` lines 53–61).
- `mezz.jei.gui.recipes.RecipeLayoutWithButtons` is `public record RecipeLayoutWithButtons<R>(IRecipeLayoutDrawable<R> recipeLayout, RecipeTransferButton transferButton, RecipeBookmarkButton bookmarkButton)`
  — useful for aligning your button next to JEI's, and `IRecipeLayoutDrawable.getRecipeTransferButtonArea()` /
  `getRecipeBookmarkButtonArea()` give the reserved rectangles.
- Without Mixin: `net.minecraftforge.client.event.ScreenEvent$Init$Post` / `$Render$Post` / `$MouseButtonPressed$Pre`
  with `event.getScreen() instanceof RecipesGui` works for drawing and hit-testing, but you cannot register a real
  widget (`Screen#addRenderableWidget` is `protected`, `renderables` is `protected`) — you must draw and hit-test
  manually. `ScreenEvent` cannot see which recipe is displayed; only `RecipesGui.isOpen()` and `getArea()` are public.

---

## 7. Plugin declaration + category/catalyst registration (Forge 1.20.1)

`mezz/jei/api/JeiPlugin.java` (entire file body):

```java
public @interface JeiPlugin { }
```

Javadoc: "This annotation lets JEI detect mod plugins. All IModPlugin must have this annotation and a constructor
with no arguments." No `@Retention`/`@Target`, no modid attribute, no `mods.toml` entry needed.

Discovery (Forge): `mezz/jei/forge/startup/ForgePluginFinder.java` scans `ModList.get().getScanData()` /
`ModFileScanData.AnnotationData` for `@JeiPlugin`, then `Class.forName(...).asSubclass(IModPlugin.class)
.getDeclaredConstructor().newInstance()` — i.e. the annotation must be present in the mod's compiled
annotation index, and the class needs a public no-arg constructor.

`mezz/jei/api/IModPlugin.java` (162 lines):

```java
public interface IModPlugin {
    ResourceLocation getPluginUid();
    default void registerItemSubtypes(ISubtypeRegistration registration);
    default <T> void registerFluidSubtypes(ISubtypeRegistration registration, IPlatformFluidHelper<T> fluidHelper);
    default void registerIngredients(IModIngredientRegistration registration);
    default void registerExtraIngredients(IExtraIngredientRegistration registration);          // @since 15.19.0
    default void registerIngredientAliases(IIngredientAliasRegistration registration);          // @since 15.15.0
    default void registerCategories(IRecipeCategoryRegistration registration);
    default void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration);
    default void registerRecipes(IRecipeRegistration registration);
    default void registerRecipeTransferHandlers(IRecipeTransferRegistration registration);
    default void registerRecipeCatalysts(IRecipeCatalystRegistration registration);
    default void registerGuiHandlers(IGuiHandlerRegistration registration);
    default void registerAdvanced(IAdvancedRegistration registration);
    default void registerRuntime(IRuntimeRegistration registration);
    default void onRuntimeAvailable(IJeiRuntime jeiRuntime);
    default void onRuntimeUnavailable();
    default void onConfigManagerAvailable(IJeiConfigManager configManager);
}
```

`mezz/jei/api/registration/IRecipeCategoryRegistration.java` (20 lines):

```java
IJeiHelpers getJeiHelpers();
void addRecipeCategories(IRecipeCategory<?>... recipeCategories);
```

`mezz/jei/api/registration/IRecipeCatalystRegistration.java` (110 lines):

```java
IIngredientManager getIngredientManager();
IJeiHelpers getJeiHelpers();
void addRecipeCatalysts(RecipeType<?> recipeType, ItemLike... ingredients);                       // @since 15.20.0
default void addRecipeCatalysts(RecipeType<?> recipeType, ItemStack... ingredients);              // -> (type, ITEM_STACK, List.of(...))
<T> void addRecipeCatalysts(RecipeType<?> recipeType, IIngredientType<T> ingredientType, List<T> ingredients);
default void addRecipeCatalyst(ItemLike itemLike, RecipeType<?>... recipeTypes);                  // @since 15.19.4
default void addRecipeCatalyst(ItemStack ingredient, RecipeType<?>... recipeTypes);
<T> void addRecipeCatalyst(IIngredientType<T> ingredientType, T ingredient, RecipeType<?>... recipeTypes);
```

Naming split: plural `addRecipeCatalysts` = *(recipeType, many ingredients)*; singular `addRecipeCatalyst`
= *(one ingredient, many recipeTypes)*.

`mezz/jei/api/registration/IRecipeRegistration.java`: `<T> void addRecipes(RecipeType<T> recipeType, List<T> recipes);`
plus `addIngredientInfo(...)` overloads and `getVanillaRecipeFactory()`.

Minimal plugin skeleton:

```java
@JeiPlugin
public final class MyJeiPlugin implements IModPlugin {
    public static final RecipeType<MyRecipe> TYPE =
        RecipeType.create("mymod", "crushing", MyRecipe.class);

    @Override public ResourceLocation getPluginUid() { return new ResourceLocation("mymod", "jei_plugin"); }

    @Override public void registerCategories(IRecipeCategoryRegistration r) {
        r.addRecipeCategories(new MyRecipeCategory(r.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration r) {
        r.addRecipes(TYPE, MyRecipes.all());
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addRecipeCatalysts(TYPE, Items.CRUSHER);   // workbench -> category
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) { /* ... */ }
}
```

---

## 8. Maven coordinates and the `-api` classifier

Repository: `https://maven.blamejared.com` (also mirrored on CurseForge/Modrinth for the mod jar itself).

```gradle
repositories { maven { url = "https://maven.blamejared.com" } }
dependencies { implementation "mezz.jei:jei-1.20.1-forge:15.20.0.106" }
```

Directory listing of `https://maven.blamejared.com/mezz/jei/jei-1.20.1-forge/15.20.0.106/` contains **only**:

| File | Size |
|---|---|
| `jei-1.20.1-forge-15.20.0.106.jar` | 1,379,220 |
| `jei-1.20.1-forge-15.20.0.106-sources.jar` | 649,011 |
| `jei-1.20.1-forge-15.20.0.106.pom` | 1,364 |
| + `.md5` / `.sha1` / `.sha256` / `.sha512` for each | |

**No `-api` classifier exists** for `mezz.jei:jei-1.20.1-forge`. Instead JEI publishes separate API artifacts:

| Artifact | Purpose | jar size |
|---|---|---|
| `mezz.jei:jei-1.20.1-forge-api:15.20.0.106` | only `mezz/jei/api/forge/ForgeTypes.class` (+ package-info); depends on common-api | 3,141 |
| `mezz.jei:jei-1.20.1-common-api:15.20.0.106` | **all** `mezz.jei.api.*` classes (196 entries, incl. `mezz/jei/api/runtime/IJeiRuntime.class`) | 117,795 |

`jei-1.20.1-forge-15.20.0.106.pom` dependencies: `jei-1.20.1-core`, `jei-1.20.1-common`, `jei-1.20.1-common-api`,
`jei-1.20.1-lib`, `jei-1.20.1-gui`, `jei-1.20.1-forge-api` (all 15.20.0.106). The `jei-1.20.1-forge` jar itself is a
fat jar: 1,098 entries including `mezz/jei/api/runtime/IJeiRuntime.class` **and**
`mezz/jei/gui/recipes/RecipesGui.class` (verified by listing the downloaded jar). So depending on
`mezz.jei:jei-1.20.1-forge:15.20.0.106` gives you both the API and the `mezz.jei.gui.*` implementation classes
at compile time; adding `mezz.jei:jei-1.20.1-common-api:15.20.0.106` alone is enough if you want API-only.

---

## Quick "absent in 15.20.0.106" checklist

- `IRecipeManager.getRecipeIngredients`, `createRecipeLayoutDrawableOrShowError`, `getRecipeButtonControllerFactories`
- `mezz.jei.api.gui.buttons.*`, `IRecipeButtonControllerFactory`, `IIconButtonController`
- `IRecipeCategory.getRecipeCatalysts()` / `getCatalysts()` / `needsRecipeBorder()` / `getSourceRecipeIds()`
- `IBookmarkManager`, `IJeiRuntime.getBookmarkManager()`
- `IIngredientManager.getTypedIngredient(...)` (use `createTypedIngredient`)
- `ICategoryExtension` (only `ICraftingCategoryExtension`, `ISmithingCategoryExtension`,
  `IExtendableSmithingRecipeCategory` under `mezz/jei/api/recipe/category/extensions/vanilla/`)
- any public API for adding widgets/buttons to `RecipesGui`
