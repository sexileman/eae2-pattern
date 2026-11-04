# AE2 15.2.16 (Minecraft 1.20.1 / Forge) — verified API facts

Source: `AppliedEnergistics/Applied-Energistics-2`, branch **`forge/1.20.1`**; every file below was actually fetched
from `raw.githubusercontent.com/.../forge/1.20.1/<repo path>`. Cited paths are repo paths (prefix `src/main/java/`).
Where a name does not exist in this branch it is marked **NOT FOUND in 15.2.16**.

This branch is the 1.20.1 "no MenuHost" API: `IContainerBlockEntity`, `IMenuHost`, `IGridNodeHost` do not exist (§8, §2).

## 1. `IActionSource` and `IActionHost`

`src/main/java/appeng/api/networking/security/IActionSource.java`
```java
public interface IActionSource {
    static IActionSource empty() { return new BaseActionSource(); }
    static IActionSource ofPlayer(Player player) { return ofPlayer(player, null); }
    static IActionSource ofPlayer(Player player, @Nullable IActionHost maybeHost) { return new PlayerSource(player, maybeHost); }
    static IActionSource ofMachine(IActionHost machine) { return new MachineSource(machine); }
    Optional<Player> player();
    Optional<IActionHost> machine();
    <T> Optional<T> context(Class<T> key);
}
```

`src/main/java/appeng/api/networking/security/IActionHost.java`
```java
public interface IActionHost {
    @Nullable IGridNode getActionableNode();
}
```

That is the **only** method — a functional interface, so the "simple custom `IActionHost`" is a lambda:
```java
IActionHost host = () -> managedNode.getNode();            // null is legal (@Nullable)
IActionSource srcMachine = IActionSource.ofMachine(host);
IActionSource srcPlayer  = IActionSource.ofPlayer(player, host);   // player + machine attribution
```

Concrete impls (`src/main/java/appeng/me/helpers/`):
- `PlayerSource.java`: `public PlayerSource(Player p)` / `public PlayerSource(Player p, @Nullable IActionHost v)`;
  `player()` → `Optional.of(this.player)`; `machine()` → `Optional.ofNullable(this.via)`; `context(Class<T>)` → `Optional.empty()`.
- `MachineSource.java`: `public MachineSource(IActionHost v)` (`requireNonNull`); `player()` empty; `machine()` → `Optional.of(this.via)`.
- `BaseActionSource.java`: all three return `Optional.empty()`; used by `IActionSource.empty()`.

`AEBaseMenu` builds its own source as `new PlayerSource(getPlayer(), getActionHost())`, so the machine half is the menu
host's `getActionableNode()` (§8).

## 2. `IGrid`, `IGridNode`, node host, `IManagedGridNode`

### `IGrid` — `src/main/java/appeng/api/networking/IGrid.java` (do not implement)
```java
public interface IGrid {
    <C extends IGridService> C getService(Class<C> iface);        // <-- get a service from the grid
    <T extends GridEvent> T postEvent(T ev);
    Iterable<Class<?>> getMachineClasses();
    Iterable<IGridNode> getMachineNodes(Class<?> machineClass);
    <T> Set<T> getMachines(Class<T> machineClass);
    <T> Set<T> getActiveMachines(Class<T> machineClass);
    Iterable<IGridNode> getNodes();
    boolean isEmpty();
    IGridNode getPivot();
    int size();
    // defaults: getTickManager(); getStorageService() = getService(IStorageService.class); getEnergyService();
    //           getCraftingService(); getPathingService(); getSpatialService();
}
```

### `IGridNode` — `src/main/java/appeng/api/networking/IGridNode.java`
```java
public interface IGridNode {
    @Nullable <T extends IGridNodeService> T getService(Class<T> serviceClass);   // node-level service, NOT grid
    Object getOwner();
    void beginVisit(IGridVisitor visitor);
    IGrid getGrid();                            // <-- node -> grid
    ServerLevel getLevel();
    Set<Direction> getConnectedSides();
    Map<Direction, IGridConnection> getInWorldConnections();
    List<IGridConnection> getConnections();
    boolean hasGridBooted();
    boolean isPowered();
    boolean meetsChannelRequirements();
    boolean hasFlag(GridFlags flag);
    int getOwningPlayerId();
    @Nullable UUID getOwningPlayerProfileId();
    double getIdlePowerUsage();
    @Nullable AEItemKey getVisualRepresentation();
    AEColor getGridColor();
    void fillCrashReportCategory(CrashReportCategory category);
    int getMaxChannels();
    int getUsedChannels();
    // defaults: isActive() = isPowered() && hasGridBooted() && meetsChannelRequirements();
    //           isOnline() = isPowered() && meetsChannelRequirements();
}
```

### Node host — `src/main/java/appeng/api/networking/IInWorldGridNodeHost.java`
`appeng/api/networking/IGridNodeHost.java` → **NOT FOUND in 15.2.16** (the interface is `IInWorldGridNodeHost`).
```java
@AutoRegisterCapability
public interface IInWorldGridNodeHost {
    @Nullable IGridNode getGridNode(Direction dir);          // dir == null for non-directional lookups
    default AECableType getCableConnectionType(Direction dir) { return AECableType.GLASS; }
}
```
A BE becomes a grid host by implementing this interface itself **or** by exposing it via the
`Capabilities.IN_WORLD_GRID_NODE_HOST` capability (GridHelper line 132 shows both routes):

`src/main/java/appeng/api/networking/GridHelper.java` (final, private ctor)
```java
public static <T extends BlockEntity> void onFirstTick(T blockEntity, Consumer<? super T> callback)   // line 76
@Nullable public static IInWorldGridNodeHost getNodeHost(Level level, BlockPos pos)                   // line 132
@Nullable public static IGridNode getExposedNode(Level level, BlockPos pos, Direction side)           // line 149
public static <T> IManagedGridNode createManagedNode(T owner, IGridNodeListener<T> listener)          // line 175
public static IGridConnection createConnection(IGridNode a, IGridNode b)                              // line 188
// also: addEventHandler(...), addGridServiceEventHandler(...), addNodeOwnerEventHandler(...)
```
`createManagedNode` body: `return new ManagedGridNode(owner, listener);`

### `IManagedGridNode` — `src/main/java/appeng/api/networking/IManagedGridNode.java`
```java
public interface IManagedGridNode {
    void destroy();
    void create(Level level, @Nullable BlockPos blockPos);
    void loadFromNBT(CompoundTag nodeData);
    void saveToNBT(CompoundTag nodeData);
    default boolean ifPresent(Consumer<IGrid> action);
    default boolean ifPresent(BiConsumer<IGrid, IGridNode> action);
    IManagedGridNode setFlags(GridFlags... flags);
    IManagedGridNode setExposedOnSides(Set<Direction> directions);
    IManagedGridNode setIdlePowerUsage(double usagePerTick);
    IManagedGridNode setVisualRepresentation(@Nullable AEItemKey visualRepresentation);
    default IManagedGridNode setVisualRepresentation(ItemStack visualRepresentation);   // -> AEItemKey.of(stack)
    default IManagedGridNode setVisualRepresentation(ItemLike visualRepresentation);    // -> AEItemKey.of(item)
    IManagedGridNode setInWorldNode(boolean accessible);
    IManagedGridNode setTagName(String tagName);
    IManagedGridNode setGridColor(AEColor gridColor);
    <T extends IGridNodeService> IManagedGridNode addService(Class<T> serviceClass, T service);
    default @Nullable IGrid getGrid();          // null when getNode() == null
    @Nullable IGridNode getNode();
    boolean isReady();  boolean isActive();  boolean isOnline();  boolean isPowered();  boolean hasGridBooted();
    void setOwningPlayerId(int ownerPlayerId);
    void setOwningPlayer(Player ownerPlayer);
}
```
`GridFlags` — `src/main/java/appeng/api/networking/GridFlags.java`: `REQUIRE_CHANNEL, COMPRESSED_CHANNEL, CANNOT_CARRY,
CANNOT_CARRY_COMPRESSED, DENSE_CAPACITY, MULTIBLOCK, PREFERRED`.

`IGridNodeListener` — `src/main/java/appeng/api/networking/IGridNodeListener.java`
```java
public interface IGridNodeListener<T> {
    void onSaveChanges(T nodeOwner, IGridNode node);
    default void onInWorldConnectionChanged(T nodeOwner, IGridNode node);
    default void onOwnerChanged(T nodeOwner, IGridNode node);        // delegates to onSaveChanges
    default void onGridChanged(T nodeOwner, IGridNode node);
    default void onStateChanged(T nodeOwner, IGridNode node, State state);
    enum State { POWER, CHANNEL, GRID_BOOT }
}
```

### How a BE becomes a grid host + action host (the AE2 pattern)
`src/main/java/appeng/me/helpers/IGridConnectedBlockEntity.java`
```java
public interface IGridConnectedBlockEntity extends IActionHost, IOwnerAwareBlockEntity, IInWorldGridNodeHost {
    IManagedGridNode getMainNode();
    default Set<Direction> getGridConnectableSides(BlockOrientation orientation);   // EnumSet.allOf(Direction.class)
    default @Nullable IGridNode getGridNode();                                     // = getMainNode().getNode()
    default IGridNode getGridNode(Direction dir);     // node only if InWorldGridNode.isExposedOnSide(dir)
    default boolean ifGridPresent(Consumer<IGrid>);
    void saveChanges();
    default void onMainNodeStateChanged(IGridNodeListener.State reason);
    default IGridNode getActionableNode();            // = getMainNode().getNode()
    default void setOwner(Player owner);              // = getMainNode().setOwningPlayer(owner)
}
```
`src/main/java/appeng/me/helpers/BlockEntityNodeListener.java`
```java
public class BlockEntityNodeListener<T extends IGridConnectedBlockEntity> implements IGridNodeListener<T> {
    public static final BlockEntityNodeListener<IGridConnectedBlockEntity> INSTANCE = new BlockEntityNodeListener<>();
    // onSaveChanges -> nodeOwner.saveChanges();  onStateChanged -> nodeOwner.onMainNodeStateChanged(state)
}
```
Minimal BE:
```java
public class MyBe extends BlockEntity implements IGridConnectedBlockEntity {
    private final IManagedGridNode mainNode = GridHelper
            .createManagedNode(this, BlockEntityNodeListener.INSTANCE)
            .setVisualRepresentation(new ItemStack(MyBlocks.MY_BE.get()))   // or setVisualRepresentation(AEItemKey)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(1.0)
            .addService(IStorageProvider.class, myStorageProvider);          // optional, see §11
    @Override public IManagedGridNode getMainNode() { return mainNode; }
    @Override public void saveChanges() { setChanged(); }
    // create:   GridHelper.onFirstTick(this, be -> be.mainNode.create(be.getLevel(), be.getBlockPos()));
    //           (or mainNode.create(...) from onLoad/clearRemoved)
    // persist:  mainNode.saveToNBT(tag) / mainNode.loadFromNBT(tag);   remove: mainNode.destroy()
}
```

## 3. `IStorageService` from a grid, and `MEStorage`

```java
IStorageService storage = grid.getService(IStorageService.class);   // <C extends IGridService> C getService(Class<C>)
MEStorage inv            = storage.getInventory();
// equivalent: IStorageService storage = grid.getStorageService();  // default method on IGrid
long inserted = inv.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, actionSource);
```
**Path gotcha:** `appeng/api/storage/IStorageService.java` is a 404; the file is
`src/main/java/appeng/api/networking/storage/IStorageService.java`.
```java
public interface IStorageService extends IGridService {
    MEStorage getInventory();
    KeyCounter getCachedInventory();
    void addGlobalStorageProvider(IStorageProvider cc);
    void removeGlobalStorageProvider(IStorageProvider cc);
    void refreshNodeStorageProvider(IGridNode node);
    void refreshGlobalStorageProvider(IStorageProvider provider);
    void invalidateCache();
}
```
`src/main/java/appeng/api/storage/MEStorage.java` — full list:
```java
public interface MEStorage {
    default boolean isPreferredStorageFor(AEKey what, IActionSource source) { return false; }
    default long insert(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
    default long extract(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
    default void getAvailableStacks(KeyCounter out) { }
    Component getDescription();
    default KeyCounter getAvailableStacks();       // new KeyCounter, calls getAvailableStacks(out), returns it
    static void checkPreconditions(AEKey what, long amount, Actionable mode, IActionSource source);
}
```
Enumeration goes through `KeyCounter` (`src/main/java/appeng/api/stacks/KeyCounter.java`), which is iterable:
```java
public final class KeyCounter implements Iterable<Object2LongMap.Entry<AEKey>> {
    public long get(AEKey key);   public void add(AEKey key, long amount);   public void set(AEKey key, long amount);
    public Set<AEKey> keySet();   public int size();   public boolean isEmpty();   public AEKey getFirstKey();
    public <T extends AEKey> T getFirstKey(Class<T> keyClass);
}
```

## 4. `AEItemKey` / `AEKey` conversions

`src/main/java/appeng/api/stacks/AEItemKey.java`
```java
public final class AEItemKey extends AEKey {
    @Nullable public static AEItemKey of(ItemStack stack);        // ItemStack -> AEItemKey, null if stack.isEmpty()
    public static AEItemKey of(ItemLike item);
    public static AEItemKey of(ItemLike item, @Nullable CompoundTag tag);
    @Nullable public static AEItemKey fromTag(CompoundTag tag);
    public static boolean matches(AEKey what, ItemStack itemStack);
    public static boolean is(AEKey what);
    public static AEKeyFilter filter();
    public Item getItem();
    public boolean matches(ItemStack stack);
    public boolean matches(Ingredient ingredient);
    public ItemStack toStack() { return toStack(1); }             // exact: delegates to toStack(1)
    public ItemStack toStack(int count);                          // count <= 0 -> ItemStack.EMPTY
    public ItemStack getReadOnlyStack();
    @Override public AEKeyType getType() { return AEKeyType.items(); }
    @Override public ResourceLocation getId() { return BuiltInRegistries.ITEM.getKey(item); }
    @Override public AEItemKey dropSecondary() { return of(item, null); }
    // getTag(), copyTag(), hasTag(), isDamaged(), getMaxStackSize(), writeToPacket(FriendlyByteBuf), fromPacket(...)
}
```
`src/main/java/appeng/api/stacks/AEKey.java` (abstract) — relevant members:
```java
public abstract AEKeyType getType();
public abstract ResourceLocation getId();
public abstract Object getPrimaryKey();
public abstract AEKey dropSecondary();
public abstract CompoundTag toTag();
public abstract void writeToPacket(FriendlyByteBuf data);
public static AEKey fromTagGeneric(CompoundTag tag);      // static, nullable-ish; restores the concrete type
public final CompoundTag toTagGeneric();
public final int getAmountPerUnit();
public String getUnitSymbol();
public int getAmountPerOperation();
public int getAmountPerByte();
public String formatAmount(long amount, AmountFormat format);
public String getModId();
public final boolean matches(@Nullable GenericStack stack);
public ItemStack wrapForDisplayOrFilter();               // = GenericStack.wrapInItemStack(this, 0)
public final Component getDisplayName();
public abstract void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos);
public boolean isTagged(TagKey<?> tag);
public boolean supportsFuzzyRangeSearch();
```
`src/main/java/appeng/api/stacks/AEKeyType.java`
```java
public static AEKeyType items()   { return AEItemKeys.INSTANCE; }
public static AEKeyType fluids();
@Nullable public static AEKeyType fromRawId(int id);
public final ResourceLocation getId();
public final Class<? extends AEKey> getKeyClass();
public final byte getRawId();
public int getAmountPerOperation();
public int getAmountPerByte();
public final AEKey tryCast(AEKey key);
public final boolean contains(AEKey key);
public boolean supportsFuzzyRangeSearch();
public final AEKeyFilter filter();
public Component getDescription();
```
Cheat-sheet: `ItemStack`→key: `AEItemKey.of(stack)`; key→stack: `key.toStack(count)`/`toStack()`/`getReadOnlyStack()`;
key→id: `key.getId()`; type test: `key.getType() == AEKeyType.items()` or `what instanceof AEItemKey`;
any key as display stack: `key.wrapForDisplayOrFilter()`.

## 5. `GenericStack`

`src/main/java/appeng/api/stacks/GenericStack.java` — it is a **record**:
```java
public record GenericStack(AEKey what, long amount) {
    public GenericStack(AEKey what, long amount);        // compact ctor: Objects.requireNonNull(what, "what")
    public AEKey what();                                 // record accessor
    public long amount();                                // record accessor
    @Nullable public static GenericStack readBuffer(FriendlyByteBuf buffer);
    public static void writeBuffer(@Nullable GenericStack stack, FriendlyByteBuf buffer);
    @Nullable public static GenericStack readTag(CompoundTag tag);
    public static CompoundTag writeTag(@Nullable GenericStack stack);
    @Nullable public static GenericStack fromItemStack(ItemStack stack);   // unwraps WrappedGenericStack; null if empty
    @Nullable public static GenericStack fromFluidStack(FluidStack stack);
    public static long getStackSizeOrZero(@Nullable GenericStack stack);
    public static ItemStack wrapInItemStack(@Nullable GenericStack stack);
    public static ItemStack wrapInItemStack(AEKey what, long amount);
    public static boolean isWrapped(ItemStack stack);
    public static GenericStack unwrapItemStack(ItemStack stack);
    public static GenericStack sum(GenericStack left, GenericStack right);
}
```
`GenericStack.fromChemical(...)` → **NOT FOUND in 15.2.16**. `GenericStack.wrapIngredient(...)` → **NOT FOUND**; the
wrapping API is `wrapInItemStack` / `isWrapped` / `unwrapItemStack`. Fields are the record components `what`/`amount`
(private final; read via `what()` / `amount()`).

## 6. `PatternDetailsHelper` + blank pattern

`src/main/java/appeng/api/crafting/PatternDetailsHelper.java` (final, all static):
```java
public static void registerDecoder(IPatternDetailsDecoder decoder);
public static boolean isEncodedPattern(ItemStack stack);
@Nullable public static IPatternDetails decodePattern(ItemStack stack, Level level);
@Nullable public static IPatternDetails decodePattern(AEItemKey what, Level level);
@Nullable public static IPatternDetails decodePattern(ItemStack stack, Level level, boolean autoRecovery);
public static ItemStack encodeProcessingPattern(GenericStack[] in, GenericStack[] out);
public static ItemStack encodeCraftingPattern(CraftingRecipe recipe, ItemStack[] in, ItemStack out,
        boolean allowSubstitutes, boolean allowFluidSubstitutes);
public static ItemStack encodeStonecuttingPattern(StonecutterRecipe recipe, AEItemKey in, AEItemKey out,
        boolean allowSubstitutes);
public static ItemStack encodeSmithingTablePattern(SmithingRecipe recipe, AEItemKey template, AEItemKey base,
        AEItemKey addition, AEItemKey out, boolean allowSubstitutes);
```
All four `encode*` exist with exactly these parameter lists (`encodeProcessingPattern` takes `GenericStack[]`; the other
three take the vanilla recipe plus resolved keys/stacks). `isEncodedPattern` delegates to
`AEPatternDecoder.INSTANCE` — `src/main/java/appeng/crafting/pattern/AEPatternDecoder.java`:
```java
public boolean isEncodedPattern(ItemStack stack) { return stack.getItem() instanceof EncodedPatternItem; }
```
`decodePattern` returns `null` when `level == null` or the item is not an `EncodedPatternItem` — always pass a `Level`.

Blank pattern — `src/main/java/appeng/core/definitions/AEItems.java` lines 178-182:
```java
public static final ItemDefinition<MaterialItem> BLANK_PATTERN = item("Blank Pattern", AEItemIds.BLANK_PATTERN, MaterialItem::new);
public static final ItemDefinition<CraftingPatternItem>       CRAFTING_PATTERN        = ... p.stacksTo(1) ...;
public static final ItemDefinition<ProcessingPatternItem>     PROCESSING_PATTERN      = ... p.stacksTo(1) ...;
public static final ItemDefinition<SmithingTablePatternItem>  SMITHING_TABLE_PATTERN  = ... p.stacksTo(1) ...;
public static final ItemDefinition<StonecuttingPatternItem>   STONECUTTING_PATTERN    = ... p.stacksTo(1) ...;
```
`BLANK_PATTERN` is `ItemDefinition<MaterialItem>` — a plain `MaterialItem`, **not** an `EncodedPatternItem`.
`src/main/java/appeng/core/definitions/ItemDefinition.java`:
```java
public class ItemDefinition<T extends Item> implements ItemLike {
    public ResourceLocation id();
    public ItemStack stack();
    public ItemStack stack(int count);
    public GenericStack genericStack(long stackSize);
    public final boolean isSameAs(ItemStack comparableStack);   // item-type equality, ignores NBT
    public final boolean isSameAs(AEKey key);
    public T asItem();
}
```
Test it with:
```java
boolean blank   = AEItems.BLANK_PATTERN.isSameAs(stack);              // preferred
boolean blank2  = stack.is(AEItems.BLANK_PATTERN.asItem());           // vanilla, equivalent
boolean encoded = PatternDetailsHelper.isEncodedPattern(stack);       // false for BLANK_PATTERN
```

## 7. Wireless terminal → `IGrid` (server side, given a `ServerPlayer`)

**No public "locate the player's wireless terminal" API exists in 15.2.16.**
- `src/main/java/appeng/api/features/Locatables.java` is a generic `long`-keyed registry whose only registered type is
  quantum network bridges — there is no terminal-locator registry:
```java
public final class Locatables {
    public static final class Type<T> {
        @Nullable public T get(Level level, long key);        // null on client level
        public void register(Level level, long key, T locatable);
        public void unregister(Level level, long key);
    }
    public static Type<IActionHost> quantumNetworkBridges();
}
```
- The real mechanism is the item: `src/main/java/appeng/items/tools/powered/WirelessTerminalItem.java`
```java
public static final IGridLinkableHandler LINKABLE_HANDLER;
private static final String TAG_ACCESS_POINT_POS = "accessPoint";
@Nullable public GlobalPos getLinkedPosition(ItemStack item);                                    // line 155
@Nullable public IGrid getLinkedGrid(ItemStack item, Level level, @Nullable Player sendMessagesTo); // line 168
public boolean openFromInventory(Player player, int inventorySlot);
protected boolean openFromInventory(Player player, int inventorySlot, boolean isReturning);
public boolean hasPower(Player player, double amount, ItemStack stack);
public boolean usePower(Player player, double amount, ItemStack stack);
protected boolean checkPreconditions(ItemStack item, Player player);
```
`getLinkedGrid` body (whole algorithm): requires `level instanceof ServerLevel`; reads `getLinkedPosition(item)`;
`serverLevel.getServer().getLevel(linkedPos.dimension())`; `Platform.getTickingBlockEntity(linkedLevel, linkedPos.pos())`;
requires `be instanceof IWirelessAccessPoint`; returns `accessPoint.getGrid()`, else `null`.
```java
// src/main/java/appeng/api/implementations/blockentities/IWirelessAccessPoint.java
public interface IWirelessAccessPoint extends IActionHost {
    DimensionalBlockPos getLocation();
    double getRange();
    boolean isActive();
    @Nullable IGrid getGrid();
}
```
So the clean path: scan the player's inventory yourself (vanilla), then:
```java
if (stack.getItem() instanceof WirelessTerminalItem terminal) {
    IGrid grid = terminal.getLinkedGrid(stack, player.level(), player);   // null if unlinked / no WAP
}
```
`getLinkedGrid` does **not** range-check. Range/security lives in the menu host
`src/main/java/appeng/helpers/WirelessTerminalMenuHost.java`:
```java
public class WirelessTerminalMenuHost extends ItemMenuHost implements IPortableTerminal, IActionHost {
    public WirelessTerminalMenuHost(Player player, @Nullable Integer slot, ItemStack itemStack,
            BiConsumer<Player, ISubMenu> returnToMainMenu);   // targetGrid = getLinkedGrid(...); sg = getStorageService()
    public MEStorage getInventory();                         // sg != null ? sg.getInventory() : null
    public IGridNode getActionableNode();                    // rangeCheck(); myWap != null ? myWap.getActionableNode() : null
    public boolean rangeCheck();                             // targetGrid.getMachines(WirelessAccessPointBlockEntity.class)
    protected double getWapSqDistance(IWirelessAccessPoint wap);
    public String getCloseHotkey();                          // HotkeyAction.WIRELESS_TERMINAL
}
```
To replicate the check headlessly, do what `rangeCheck()` does: iterate
`grid.getMachines(WirelessAccessPointBlockEntity.class)` and compare squared distance against `getRange()`/`getLocation()`
for an `isActive()` WAP — plus power via `IAEItemPowerStorage`
(`src/main/java/appeng/api/implementations/items/IAEItemPowerStorage.java`):
```java
double injectAEPower(ItemStack stack, double amount, Actionable mode);
double extractAEPower(ItemStack stack, double amount, Actionable mode);
double getAEMaxPower(ItemStack stack);
double getAECurrentPower(ItemStack stack);
AccessRestriction getPowerFlow(ItemStack stack);
double getChargeRate(ItemStack stack);
```
`WirelessTerminalItem` also implements `IMenuItem` (`getMenuType()` → `MEStorageMenu.WIRELESS_TYPE`) and
`getMenuHost(...)` → `WirelessTerminalMenuHost`, which is the supported way to obtain an `MEStorage`-backed,
`IActionHost`-capable host for a carried terminal — but it is not needed just to get the `IGrid`.

## 8. `MEStorageMenu`, `AEBaseMenu`, minimal custom menu

`src/main/java/appeng/menu/me/common/MEStorageMenu.java`
```java
public class MEStorageMenu extends AEBaseMenu
        implements IConfigManagerListener, IConfigurableObject, IMEInteractionHandler {
    public static final MenuType<MEStorageMenu> TYPE =
            MenuTypeBuilder.<MEStorageMenu, ITerminalHost>create(MEStorageMenu::new, ITerminalHost.class)
                    .build("item_terminal");
    public static final MenuType<MEStorageMenu> PORTABLE_ITEM_CELL_TYPE  = ...;   // IPortableTerminal.class
    public static final MenuType<MEStorageMenu> PORTABLE_FLUID_CELL_TYPE = ...;   // IPortableTerminal.class
    public static final MenuType<MEStorageMenu> WIRELESS_TYPE            = ...;   // IPortableTerminal.class, "wirelessterm"

    public MEStorageMenu(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host) {    // line 156
        this(menuType, id, ip, host, true);
    }
    protected MEStorageMenu(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host, boolean bindInventory); // 160

    @Nullable protected final MEStorage storage;
    @Nullable protected final IEnergySource powerSource;
    @Nullable private IGridNode networkNode;
    public IGridNode getNetworkNode();
    public boolean isKeyVisible(AEKey key);
}
```
`getActionSource()` is **not** overridden here — it comes from `AEBaseMenu` and returns
`new PlayerSource(getPlayer(), getActionHost())`, where `getActionHost()` is the host when the host implements `IActionHost`.

`src/main/java/appeng/menu/AEBaseMenu.java`
```java
public abstract class AEBaseMenu extends AbstractContainerMenu {                       // line 77
    private final IActionSource mySrc;
    @Nullable private final BlockEntity blockEntity;
    @Nullable private final IPart part;
    @Nullable protected final ItemMenuHost itemMenuHost;

    public AEBaseMenu(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {   // line 104
        // blockEntity = host instanceof BlockEntity; part = host instanceof IPart;
        // itemMenuHost = host instanceof ItemMenuHost;
        // if (host != null && blockEntity == null && part == null && itemMenuHost == null)
        //     throw new IllegalArgumentException("Must have a valid host, instead " + host + " in " + playerInventory);
        // this.mySrc = new PlayerSource(getPlayer(), this.getActionHost());
    }
    protected final IActionHost getActionHost();          // line 124: itemMenuHost / blockEntity / part instanceof IActionHost
    protected final boolean isActionHost();               // line 140
    public Player getPlayer();                            // line 150
    public IActionSource getActionSource() { return this.mySrc; }   // line 154
    public Inventory getPlayerInventory();                // line 158
    public void lockPlayerInventorySlot(int slot);        // line 162
    public boolean isPlayerInventorySlotLocked(int slot); // line 168
    public Object getTarget();                            // line 172
    @Nullable public BlockEntity getBlockEntity();        // line 182
    protected void createPlayerInventorySlots(Inventory playerInventory);   // line 186
    public void clicked(int slotId, int button, ClickType clickType, Player player);   // line 206
    protected void addSlot(Slot slot, SlotSemantic semantic);              // line 215
    protected final void addClientSideSlot(Slot slot);                     // line 230
    protected final void removeClientSideSlot(Slot slot);                  // line 248
}
```
**Do you need a `MenuHost` / `IContainerBlockEntity`? No.** `src/main/java/appeng/api/implementations/blockentities/IContainerBlockEntity.java`
and `src/main/java/appeng/menu/IMenuHost.java` are both **NOT FOUND in 15.2.16**. The 4th ctor argument is a plain
`Object host` that must be a `BlockEntity`, `IPart` or `ItemMenuHost` (otherwise it throws). Any `BlockEntity` works —
it does not have to be an `AEBaseBlockEntity`.

### Minimal custom block menu with an `MEStorage`
```java
// 1. BE must be an IActionHost so getActionSource() carries machine attribution
//    (IGridConnectedBlockEntity already supplies getActionableNode()).
public class MyBe extends BlockEntity implements IGridConnectedBlockEntity { ... }

// 2. MenuFactory signature: C create(int containerId, Inventory playerInv, I accessObj)
public class MyMenu extends AEBaseMenu {
    private final MEStorage storage;
    private final IGridNode node;
    public MyMenu(int containerId, Inventory ip, MyBe be) {      // be is a BlockEntity -> valid host
        super(HOLDER.TYPE, containerId, ip, be);
        this.storage = be.getStorageForMenu();
        this.node = be.getActionableNode();
        createPlayerInventorySlots(ip);
    }
    public MEStorage getStorage() { return storage; }
    public IGrid getGrid() { return node != null ? node.getGrid() : null; }
    // IActionSource src = getActionSource();     // PlayerSource(player, be) because be is IActionHost
}
```
`src/main/java/appeng/menu/implementations/MenuTypeBuilder.java` (nested factories + build):
```java
@FunctionalInterface public interface MenuFactory<C, I> { C create(int containerId, Inventory playerInv, I accessObj); }
@FunctionalInterface public interface TypedMenuFactory<C extends AbstractContainerMenu, I>
        { C create(MenuType<C> type, int containerId, Inventory playerInv, I accessObj); }

public static <C extends AEBaseMenu, I> MenuTypeBuilder<C, I> create(MenuFactory<C, I> factory, Class<I> hostInterface);
public static <C extends AEBaseMenu, I> MenuTypeBuilder<C, I> create(TypedMenuFactory<C, I> factory, Class<I> hostInterface);
public MenuTypeBuilder<M, I> withMenuTitle(Function<I, Component> menuTitleStrategy);
public MenuTypeBuilder<M, I> withInitialData(InitialDataSerializer<I>, InitialDataDeserializer<M, I>);
public MenuType<M> build(String id);   // AppEng.makeId(id), IForgeMenuType.create(this::fromNetwork),
                                       // InitMenuTypes.queueRegistration(id, type), MenuOpener.addOpener(type, this::open)
```
```java
public static final MenuType<MyMenu> TYPE =
        MenuTypeBuilder.<MyMenu, MyBe>create(MyMenu::new, MyBe.class).build("my_menu");
// if the menu ctor needs the MenuType (AE2's own menus do), use the typed form:
// MenuTypeBuilder.<MyMenu, MyBe>create((type, id, ip, be) -> new MyMenu(type, id, ip, be), MyBe.class).build("my_menu");
MenuOpener.open(TYPE, player, MenuLocators.forBlockEntity(be));    // server side; also MenuOpener.open(type, player, locator, boolean fromSubMenu)
```
`src/main/java/appeng/menu/locator/MenuLocators.java`: `forBlockEntity(BlockEntity te)` (69), `forPart(AEBasePart)` (76),
`forItemUseContext(UseOnContext)` (85), `forHand(Player, InteractionHand)` (94), `forInventorySlot(int)` (99),
`writeToPacket(FriendlyByteBuf, T locator)` (55), `readFromPacket(FriendlyByteBuf)` (62).
`src/main/java/appeng/menu/MenuOpener.java`: `open(MenuType<?>, Player, MenuLocator)` (51),
`open(MenuType<?>, Player, MenuLocator, boolean fromSubMenu)` (55), `returnTo(...)` (47), `addOpener(MenuType<T>, Opener)` (43).
`src/main/java/appeng/menu/locator/BlockEntityLocator.java`: `public <T> @Nullable T locate(Player player, Class<T> hostInterface)`
does `hostInterface.isInstance(blockEntity) ? cast : (log warning, null)` — so a BE-based menu opened via
`forBlockEntity` only resolves if the BE implements the menu's declared host interface (relevant for `ITerminalHost`-typed
menus such as `MEStorageMenu`, not for `MyBe`-typed ones).

### If you instead subclass `MEStorageMenu` (Option B)
`src/main/java/appeng/api/storage/ITerminalHost.java`
```java
public interface ITerminalHost extends IUpgradeableObject, IConfigurableObject, ISubMenuHost {
    @Nullable MEStorage getInventory();
    @Nullable default String getCloseHotkey() { return null; }
}
// ISubMenuHost: void returnToMainMenu(Player player, ISubMenu subMenu); ItemStack getMainMenuIcon();
// IUpgradeableObject: getUpgrades() / getInstalledUpgrades(ItemLike) / isUpgradedWith(ItemLike)
// IConfigurableObject: IConfigManager getConfigManager();
// IPortableTerminal: public interface IPortableTerminal extends ITerminalHost, IEnergySource
```
Constraints: `this.storage = host.getInventory()` and if it is `null` the menu calls `setValidMenu(false)` (closes);
`storage` is `null` on the client. Power: `IPortableTerminal`/`IMEChest` hosts are the `IEnergySource`, otherwise an
`IActionHost` host produces `new ChannelPowerSrc(networkNode, grid.getEnergyService())` (needs a live grid when the menu
opens). It also wires view cells (`IViewCellStorage`), the toolbox menu and `setupUpgrades(host.getUpgrades())` when
`bindInventory == true`.

## 9. `appeng.api.config.Actionable`

`src/main/java/appeng/api/config/Actionable.java`
```java
public enum Actionable {
    MODULATE(FluidAction.EXECUTE),
    SIMULATE(FluidAction.SIMULATE);

    public static Actionable of(FluidAction action);    // EXECUTE -> MODULATE, SIMULATE -> SIMULATE
    public static Actionable ofSimulate(boolean simulate);
    public FluidAction getFluidAction();
    public boolean isSimulate();
}
```
Exactly two constants: `MODULATE`, `SIMULATE` (there is no `EXECUTE` constant).

## 11. `IStorageMounts` / `IStorageProvider` — both exist

`src/main/java/appeng/api/storage/IStorageMounts.java`
```java
public interface IStorageMounts {
    int DEFAULT_PRIORITY = 0;
    default void mount(MEStorage inventory);        // priority DEFAULT_PRIORITY
    void mount(MEStorage inventory, int priority);
}
```
`src/main/java/appeng/api/storage/IStorageProvider.java`
```java
public interface IStorageProvider extends IGridNodeService {
    void mountInventories(IStorageMounts storageMounts);
    static void requestUpdate(IManagedGridNode managedNode);   // -> grid.getStorageService().refreshNodeStorageProvider(node)
}
```
Yes — this is how a custom BE exposes an `MEStorage` to the network:
```java
public class MyStorageProvider implements IStorageProvider {
    private final MEStorage storage;
    @Override public void mountInventories(IStorageMounts mounts) { mounts.mount(storage); }
}
// BE:
managedNode.addService(IStorageProvider.class, new MyStorageProvider(myStorage));
IStorageProvider.requestUpdate(managedNode);    // only needed when the mounted set/priorities change at runtime
```
`IStorageProvider` is documented as a grid **node** service: `IStorageService` picks it up automatically on node
join/leave. For non-node-wide providers use `IStorageService.addGlobalStorageProvider(IStorageProvider)` /
`removeGlobalStorageProvider(...)`.

## Explicitly NOT FOUND in 15.2.16 (checked against the full `forge/1.20.1` tree)

- `appeng/api/networking/IGridNodeHost.java` — interface is `IInWorldGridNodeHost`.
- `appeng/api/storage/IStorageService.java` — real path `appeng/api/networking/storage/IStorageService.java`.
- `appeng/api/implementations/blockentities/IContainerBlockEntity.java` — gone in 1.20.1.
- `appeng/menu/IMenuHost.java` — gone in 1.20.1; `AEBaseMenu` takes `Object host`.
- `appeng/me/helpers/GridHelper.java` — real path `appeng/api/networking/GridHelper.java`.
- `appeng/items/tools/powered/WirelessTerminalMenuHost.java` — real path `appeng/helpers/WirelessTerminalMenuHost.java`.
- `MenuFactory` / `TypedMenuFactory` as top-level files — they are nested interfaces of `MenuTypeBuilder`.
- `GenericStack.fromChemical(...)`, `GenericStack.wrapIngredient(...)` — not in `GenericStack.java`.
- Any public terminal locator on `Locatables` — only `quantumNetworkBridges()` exists.
