.. _ws_ft_seznameni:

----------------
Rychlé seznámení
----------------

Rozhraní umožňuje přenos souborů z klienta na server. 

Postup volální metod serveru:

#. Zahájení přenosu metodou :token:`Begin`
#. Odeslání datových balíčků metodou :token:`Send`, 
   poslední balíček je s příznakem :token:`last`.
#. Dokončení přenosu a jeho potvrzení metodou :token:`Finish`

Stav probíhajícího přenosu je možné zjistit pomocí volání :token:`GetStatus`.
Probíhající přenos je možné přerušit pomocí metody :token:`Abort`.
