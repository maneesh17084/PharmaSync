package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Category;

import java.util.List;

public class CategoryDao {

    public List<Category> findAll() {
        return Jdbc.query("SELECT id, name, description FROM categories ORDER BY name",
                rs -> new Category(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
    }

    public int insert(String name, String description) {
        return Jdbc.insert("INSERT INTO categories (name, description) VALUES (?, ?)", name, description);
    }

    public void update(int id, String name, String description) {
        Jdbc.update("UPDATE categories SET name = ?, description = ? WHERE id = ?", name, description, id);
    }

    public long countMedicines(int id) {
        return Jdbc.queryLong("SELECT COUNT(*) FROM medicines WHERE category_id = ?", id);
    }

    public void delete(int id) {
        Jdbc.update("DELETE FROM categories WHERE id = ?", id);
    }
}
