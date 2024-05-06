.. _ws_fts:

=====================
File Tranfser Service
=====================

Služba slouží pro přenos adresáře a v něm uložených souborů.
Služba umožňuje přenos i velkých objemů dat. Je možné, aby 
přenášený adresář obsahoval hlubší a košatější hierarchii.
Stejně tak je možné pomocí služby efektivně přenášet větší
množství menších souborů nebo i velké soubory.

Služba nemá v sobě zabudovány konkrétní technické limity.
Přesto je vhodné ji použít za následujících podmínek:

=============================================== ================
Položka                                         Doporučení
=============================================== ================
Počet přenášených položek (souborů a adresářů)  méně než 100 000
Počet rámců přenosu                             méně než  10 000
Velikost jednoho rámce                          ~ 10 MB
=============================================== ================

.. toctree::

  ft/seznameni.rst
  ft/popis.rst
  ft/refdoc.rst
