package org.example.rougevolley.ecs;

import javafx.geometry.Point2D;

import java.util.*;

/**
 * 游戏实体 —— 组合式组件容器，全局唯一 UUID 标识
 * <p>
 * 组件存储以类型为键，每实体每种组件仅一个实例。
 * 实体之间通过 UUID 判等。
 */
public class Entity {

    private final String uuid;
    private final Map<Class<? extends Component>, Component> components = new LinkedHashMap<>();

    private Point2D position;
    private boolean active = true;
    private Object userData; // 可附加渲染相关数据

    /**
     * @param position 实体初始位置
     */
    public Entity(Point2D position) {
        if (position == null) {
            throw new IllegalArgumentException("Entity position must not be null");
        }
        this.uuid = UUID.randomUUID().toString();
        this.position = position;
    }

    // ── 组件管理 ──

    /**
     * 添加组件，相同类型会覆盖旧组件
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> void addComponent(T component) {
        Objects.requireNonNull(component, "Component must not be null");
        Component old = components.put(component.getClass(), component);
        if (old != null) {
            old.onDetach(this);
        }
        component.onAttach(this);
    }

    /**
     * 按类型获取组件
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> Optional<T> getComponent(Class<T> type) {
        return Optional.ofNullable((T) components.get(type));
    }

    /**
     * 是否拥有指定类型的组件
     */
    public boolean hasComponent(Class<? extends Component> type) {
        return components.containsKey(type);
    }

    /**
     * 移除组件
     */
    public void removeComponent(Class<? extends Component> type) {
        Component comp = components.remove(type);
        if (comp != null) {
            comp.onDetach(this);
        }
    }

    /**
     * 获取所有组件（不可变视图）
     */
    public Collection<Component> getAllComponents() {
        return Collections.unmodifiableCollection(components.values());
    }

    // ── 生命周期 ──

    /**
     * 每帧更新所有组件
     */
    public void onUpdate(double dt) {
        if (!active) return;
        // 快照迭代，防止组件在 onUpdate 中修改组件映射导致 CME
        for (Component comp : new ArrayList<>(components.values())) {
            comp.onUpdate(this, dt);
        }
    }

    /**
     * 销毁实体，清除所有组件
     */
    public void destroy() {
        active = false;
        for (Component comp : components.values()) {
            comp.onDetach(this);
        }
        components.clear();
    }

    // ── Getters / Setters ──

    public String getUuid() {
        return uuid;
    }

    public Point2D getPosition() {
        return position;
    }

    public void setPosition(Point2D position) {
        this.position = Objects.requireNonNull(position);
    }

    /** 便捷方法 */
    public double getX() { return position.getX(); }
    public double getY() { return position.getY(); }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** 附加数据（用于绑定渲染层对象等） */
    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity entity)) return false;
        return uuid.equals(entity.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
