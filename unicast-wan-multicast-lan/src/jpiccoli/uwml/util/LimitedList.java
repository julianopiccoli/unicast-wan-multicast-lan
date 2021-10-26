package jpiccoli.uwml.util;

import java.util.ArrayList;

public class LimitedList<E> {

    private int capacity;
    private ArrayList<E> list;
    
    public LimitedList() {
        capacity = 10;
        list = new ArrayList<E>();
    }
    
    public LimitedList(int capacity) {
        this.capacity = capacity;
        list = new ArrayList<E>(capacity);
    }
    
    // Os métodos abaixo deveriam ser todos sincronizados.
    // Entretanto, como estes métodos serão chamados sempre pelas mesmas
    // threads nesta implementação, não há necessidade de realizar esta sincronização
    // Assim, ganha-se em desempenho.
    
    public boolean add(E e) {
        if (list.size() >= capacity)
            list.remove(0);
        return list.add(e);
    }

    public E get(int index) {
        return list.get(index);
    }
    
    public int size() {
        return list.size();
    }
    
    public int indexOf(E e) {
        return list.indexOf(e);
    }
    
    public boolean contains(E e) {
        return list.contains(e);
    }
    
    public E remove(int index) {
        return list.remove(index);
    }
    
    public boolean remove(E e) {
        return list.remove(e);
    }

}
