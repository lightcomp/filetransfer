.. _ws_ft_popis:

------------------
Způsob přenosu dat
------------------

Služba File Transfer umožňuje přenos dat z klienta na server nebo
ze serveru na klienta. Klientem se rozumí strana, která aktivně
volá server. Server odpovídá na požadavky klientů. Server může
paralelně oblushovat více klientů/přenosů.


Rozsah přenášených dat
----------------------

.. table:: Přenášená data
  :widths: 20 60
  
  ============ ===============
  Fáze přenosu Přenášená data
  ============ ===============
  Zahájení     Klient zasílá na server povinně informaci 
               o typu přenosu. Volitelně klient zasílá svůj identifikátor přenosu
               a doplňující informace o přenosu a to ve formě
               binárních dat nebo XML souboru s daty.
  Přenos dat   Předmětem přenosu je vždy obsah jednoho adresáře na souborovém systému.
               Přenášený adresář je tvořen sadou souborů a dalších podadresářů.
               Adresářová struktura se vždy přenáší a zpracovává do hloubky.
               
               V technické specifikace typu přenosu se určí, 
               zda se jedná o přenos ze serveru na klienta nebo
               z klienta na server.
  Potvrzení    Na závěr každého přenosu se pomocí
               samostatné metody provede jeho potvrzení. 
               Součástí odpovědi o úspěšném přenosu dat může
               server klientovi předat doplňující identifikátor
               výsledku, další doplňující informace a to ve formě
               binárních dat nebo XML souboru s daty.
  ============ ===============

Každé užití služby pro přenos dat musí mít upřesněny tyto parametry:

Typ přenosu
  Přenos je určen svým typem. Typ je řetězec na základě, kterého 
  klient i server rozpozná typ přenášených dat. Pro každý typ přenosu
  je také určen směr přenosu dat (zda server zasílá obsah adresáře klientovi
  nebo obráceně, zda klient zasílá obsah adresáře serveru).

Struktura přenášených dat
  Služba File Transfer nemá konkrétní požadavky na přenášená data. 
  Požadavky na jejich formát jsou součástí popisu užití služby.

.. _ws_ft_popis_sec:

Zabezpečení přenášených dat
-----------------------------

Přenos dat probíhá pomocí SOAP/HTTP s možností šifrovaného přenosu 
protokolem HTTPS.

Každý přenášený soubor je zabezpečen svým kontrolním
součtem. Algoritmus pro výpočet kontrolního součtu je SHA-512. Na konci 
posledního zasílaného bloku daného souboru (struktura :token:`FileEnd`) se 
přenáší 64bytů, které tvoří vypočtený kontrolní součet souboru. Přijímající strana 
při ukládání souboru ověřuje shodu vypočteného kontrolního součtu s očekávanou
hodnotou.

 
Funkce tvořící API
------------------

Přenosové API je tvořeno celkem šesti funkcemi. 

=========== ===============
Funkce      Účel
=========== ===============
``Begin``   Zahájení přenosu a jeho nastavení.
``Send``    Odeslání dat z klienta na server.
``Receive`` Příjem dat klientem ze serveru.
``Abort``   Přerušení přenosu dat.
``Finish``  Dokončení přenosu dat a jeho potvrzení.
``Status``  Zjištění stavu přenosu.
=========== ===============

Jednotlivé funkce jsou popsány v `referenční dokumentaci <../../ws-specs/ft/index.html?guid=170E817D-DF64-4970-A293-285FFD07781D>`_.


Postup volání služeb pro přenos z klienta na server
---------------------------------------------------

#. Zahájení přenosu metodou ``Begin``
#. Odeslání datových balíčků metodou ``Send``, 
   poslední balíček je s příznakem :token:`last`.
#. Dokončení přenosu a jeho potvrzení metodou ``Finish``



Postup volání služeb pro přenos ze serveru na klienta
-----------------------------------------------------

#. Zahájení přenosu metodou ``Begin``
#. Odeslání datových balíčků metodou ``Receive``, 
   poslední balíček je s příznakem :token:`last`.
#. Dokončení přenosu a jeho potvrzení metodou ``Finish``


Společné postupy pro oba typy přenosů
-------------------------------------

Stav probíhajícího přenosu je možné zjistit pomocí volání ``GetStatus``.
Probíhající přenos je možné přerušit pomocí metody ``Abort``.

Operace ``Begin`` a ``Send`` mohou trvat delší
dobu a předpokládá se jejich asynchronní zpracování na serveru.
Pokud předchozí operace nebyla plně dokončena dojde při volání
další operace k výjimce s typem ``BUSY``. V takovém 
případě by se měl klient o operaci pokusit později.
