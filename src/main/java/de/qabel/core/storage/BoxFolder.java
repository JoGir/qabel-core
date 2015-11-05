package de.qabel.core.storage;

import java.util.Arrays;

class BoxFolder {
	String name;
	byte[] key;
	String ref;

	public BoxFolder(String ref, String name, byte[] key) {
		this.name = name;
		this.key = key;
		this.ref = ref;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BoxFolder boxFolder = (BoxFolder) o;

		if (name != null ? !name.equals(boxFolder.name) : boxFolder.name != null) return false;
		if (!Arrays.equals(key, boxFolder.key)) return false;
		return !(ref != null ? !ref.equals(boxFolder.ref) : boxFolder.ref != null);

	}

	@Override
	public int hashCode() {
		int result = name != null ? name.hashCode() : 0;
		result = 31 * result + (key != null ? Arrays.hashCode(key) : 0);
		result = 31 * result + (ref != null ? ref.hashCode() : 0);
		return result;
	}

}
